package com.raghunath.hotelview.service.admin;

import com.raghunath.hotelview.entity.Employee;
import com.raghunath.hotelview.entity.EmployeeRefreshToken;
import com.raghunath.hotelview.repository.EmployeeRefreshTokenRepository;
import com.raghunath.hotelview.repository.EmployeeRepository;
import com.raghunath.hotelview.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final EmployeeRefreshTokenRepository employeeRefreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public String registerEmployee(Employee emp, String hotelId) {
        if (employeeRepository.existsByUsername(emp.getUsername())) {
            throw new RuntimeException("Username already taken!");
        }

        emp.setHotelId(hotelId);
        emp.setPassword(passwordEncoder.encode(emp.getPassword()));
        emp.setActive(true);
        if (emp.getMaxLogins() <= 0) emp.setMaxLogins(1);

        employeeRepository.save(emp);
        return "Employee " + emp.getName() + " registered successfully as " + emp.getRole();
    }

    public Map<String, String> login(String username, String password) {
        Employee emp = employeeRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!emp.isActive()) throw new RuntimeException("Account is disabled");

        if (passwordEncoder.matches(password, emp.getPassword())) {

            // 🚀 SMART SESSION MANAGEMENT (Evict oldest session instead of blocking)
            List<EmployeeRefreshToken> activeSessions = employeeRefreshTokenRepository.findByUserIdOrderByIdAsc(emp.getId());

            // Establish fallback limit of at least 1 allowed login if maxLogins is null/0
            int allowedLogins = (emp.getMaxLogins() > 0) ? emp.getMaxLogins() : 1;

            // If user has hit or exceeded the limit, delete the oldest record from the DB
            if (activeSessions.size() >= allowedLogins) {
                EmployeeRefreshToken oldestSession = activeSessions.get(0);
                employeeRefreshTokenRepository.delete(oldestSession);
            }

            // Generate Tokens (3 arguments match your signature payload requirements)
            String accessToken = jwtUtil.generateAccessToken(emp.getId(), emp.getHotelId(), emp.getRole());
            String refreshToken = jwtUtil.generateRefreshToken(emp.getId(), emp.getHotelId(), emp.getRole());

            // Save new active session token metadata
            EmployeeRefreshToken et = EmployeeRefreshToken.builder()
                    .userId(emp.getId())
                    .token(refreshToken)
                    .expiryDate(LocalDateTime.now().plusDays(7))
                    .build();
            employeeRefreshTokenRepository.save(et);

            return Map.of(
                    "accessToken", accessToken,
                    "refreshToken", refreshToken,
                    "role", emp.getRole(),
                    "name", emp.getName()
            );
        }
        throw new RuntimeException("Invalid credentials");
    }

    public Map<String, String> refreshEmployeeToken(String refreshToken) {
        if (refreshToken == null || !jwtUtil.validateToken(refreshToken.trim())) {
            throw new RuntimeException("Refresh token is invalid or expired");
        }

        String cleanToken = refreshToken.trim();

        // Find the existing session document
        EmployeeRefreshToken storedToken = employeeRefreshTokenRepository.findByToken(cleanToken)
                .orElseThrow(() -> new RuntimeException("Session revoked."));

        String empId = jwtUtil.extractUserId(cleanToken);
        String hotelId = jwtUtil.extractHotelId(cleanToken);
        String role = jwtUtil.extractRole(cleanToken);

        if (!storedToken.getUserId().equals(empId)) {
            throw new RuntimeException("Identity mismatch.");
        }

        // Generate NEW pair (3 arguments only)
        String newAccessToken = jwtUtil.generateAccessToken(empId, hotelId, role);
        String newRefreshToken = jwtUtil.generateRefreshToken(empId, hotelId, role);

        // UPDATE the existing document (Rotation)
        storedToken.setToken(newRefreshToken);
        storedToken.setExpiryDate(LocalDateTime.now().plusDays(7));
        employeeRefreshTokenRepository.save(storedToken);

        return Map.of(
                "accessToken", newAccessToken,
                "refreshToken", newRefreshToken
        );
    }

    public String updateEmployee(String empId, String hotelId, Employee details) {
        Employee existingEmp = employeeRepository.findById(empId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        // Security Check: Ensure employee belongs to the admin's hotel
        if (!existingEmp.getHotelId().equals(hotelId)) {
            throw new RuntimeException("Unauthorized to update this employee");
        }

        // Update basic fields
        if (details.getName() != null) existingEmp.setName(details.getName());
        if (details.getRole() != null) existingEmp.setRole(details.getRole());
        existingEmp.setActive(details.isActive());

        if (details.getMaxLogins() > 0) {
            existingEmp.setMaxLogins(details.getMaxLogins());
        }

        // Handle Password Update (if provided)
        if (details.getPassword() != null && !details.getPassword().isEmpty()) {
            existingEmp.setPassword(passwordEncoder.encode(details.getPassword()));
        }

        employeeRepository.save(existingEmp);
        return "Employee " + existingEmp.getName() + " updated successfully";
    }

    public void deleteEmployee(String empId, String hotelId) {
        Employee emp = employeeRepository.findById(empId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        if (!emp.getHotelId().equals(hotelId)) {
            throw new RuntimeException("Unauthorized to delete this employee");
        }

        // Remove all active refresh tokens (force logout everywhere)
        employeeRefreshTokenRepository.deleteByUserId(empId);

        employeeRepository.delete(emp);
    }

    public void logoutEmployee(String empId, String refreshToken) {
        employeeRefreshTokenRepository.deleteByToken(refreshToken.trim());
    }

    public List<Employee> getMyStaff(String hotelId) {
        return employeeRepository.findAllByHotelId(hotelId);
    }
}