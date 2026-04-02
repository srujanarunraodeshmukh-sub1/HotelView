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

            // --- STRICT BLOCK LOGIC ---
            long activeSessions = employeeRefreshTokenRepository.countByUserId(emp.getId());
            if (activeSessions >= emp.getMaxLogins()) {
                throw new RuntimeException("Login limit reached (" + emp.getMaxLogins() + "). Logout elsewhere.");
            }

            // Generate Tokens (3 arguments only - version removed)
            String accessToken = jwtUtil.generateAccessToken(emp.getId(), emp.getHotelId(), emp.getRole());
            String refreshToken = jwtUtil.generateRefreshToken(emp.getId(), emp.getHotelId(), emp.getRole());

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

    public void logoutEmployee(String empId, String refreshToken) {
        employeeRefreshTokenRepository.deleteByToken(refreshToken.trim());
    }

    public List<Employee> getMyStaff(String hotelId) {
        return employeeRepository.findAllByHotelId(hotelId);
    }
}