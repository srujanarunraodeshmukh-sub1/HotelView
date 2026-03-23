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
        // Ensure maxLogins is set (defaulting to 1 if not provided in request)
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
                throw new RuntimeException("Login limit reached (" + emp.getMaxLogins() +
                        "). Please logout from another device first.");
            }

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
        // 1. Basic Null Check & Physical Validation (The "Real JWT" Check)
        if (refreshToken == null || !jwtUtil.validateToken(refreshToken.trim())) {
            throw new RuntimeException("Refresh token is invalid or expired");
        }

        String cleanToken = refreshToken.trim();

        // 2. THE OWNER'S CHECK (The Database Truth)
        // If you delete this from MongoDB, the employee is KICKED OUT here.
        EmployeeRefreshToken storedToken = employeeRefreshTokenRepository.findByToken(cleanToken)
                .orElseThrow(() -> new RuntimeException("Employee session has been revoked. Access Denied."));

        // 3. Extraction of Identity Claims
        String empId = jwtUtil.extractUserId(cleanToken);
        String hotelId = jwtUtil.extractHotelId(cleanToken);
        String role = jwtUtil.extractRole(cleanToken);

        // Security Guard: Cross-verify the token identity against the database record
        // This prevents one employee from potentially using a stolen token structure
        if (!storedToken.getUserId().equals(empId)) {
            throw new RuntimeException("Identity mismatch. Security alert triggered.");
        }

        // --- 4. TOKEN ROTATION (Enterprise Production Standard) ---
        // Generate a fresh set to replace the used ones
        String newAccessToken = jwtUtil.generateAccessToken(empId, hotelId, role);
        String newRefreshToken = jwtUtil.generateRefreshToken(empId, hotelId, role);

        // 5. UPDATE the existing Database Record
        // Overwriting the old token ensures 'maxLogins' count remains accurate (doesn't increase)
        storedToken.setToken(newRefreshToken);
        storedToken.setExpiryDate(LocalDateTime.now().plusDays(7));
        employeeRefreshTokenRepository.save(storedToken);

        // 6. Return the new pair
        return Map.of(
                "accessToken", newAccessToken,
                "refreshToken", newRefreshToken
        );
    }

    public void logoutEmployee(String empId, String refreshToken) {
        // 1. Trim to avoid Postman copy-paste spaces
        String cleanToken = refreshToken.trim();

        // 2. Call the repository instance (lowercase employeeRefreshTokenRepository)
        Long deletedCount = employeeRefreshTokenRepository.deleteByToken(cleanToken);

        if (deletedCount == 0) {
            System.out.println("DEBUG: No token found for user " + empId + " matching: " + cleanToken);
        } else {
            System.out.println("DEBUG: Successfully deleted " + deletedCount + " session(s)");
        }
    }

    public List<Employee> getMyStaff(String hotelId) {
        return employeeRepository.findAllByHotelId(hotelId);
    }
}