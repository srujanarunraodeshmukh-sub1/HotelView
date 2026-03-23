package com.raghunath.hotelview.service.admin;

import com.raghunath.hotelview.dto.admin.LoginRequest;
import com.raghunath.hotelview.dto.admin.LoginResponse;
import com.raghunath.hotelview.entity.Admin;
import com.raghunath.hotelview.entity.AdminRefreshToken;
import com.raghunath.hotelview.repository.AdminRefreshTokenRepository;
import com.raghunath.hotelview.repository.AdminRepository;
import com.raghunath.hotelview.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final AdminRepository adminRepository;
    private final AdminRefreshTokenRepository adminRefreshTokenRepository;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder;

    public LoginResponse login(LoginRequest request) {

        Admin admin = adminRepository.findByMobile(request.getMobile())
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        // 1. Basic Security Checks
        if (!admin.isApproved()) throw new RuntimeException("Hotel not approved.");
        if (!admin.isActive()) throw new RuntimeException("Subscription inactive.");

        if (!passwordEncoder.matches(request.getPassword(), admin.getPassword())) {
            throw new RuntimeException("Invalid password");
        }

        // --- 2. STRICT BLOCK LOGIC ---
        // Count existing sessions for this hotelId/admin
        long activeSessions = adminRefreshTokenRepository.countByUserId(admin.getHotelId());

        // Use maxLogins from entity (default to 1 if not set)
        int allowedLogins = (admin.getMaxLogins() > 0) ? admin.getMaxLogins() : 1;

        if (activeSessions >= allowedLogins) {
            throw new RuntimeException("Login limit reached (" + allowedLogins +
                    "). Please logout from another device first.");
        }

        // 3. Generate Tokens
        String accessToken = jwtUtil.generateAccessToken(admin.getHotelId(), admin.getHotelId(), "ADMIN");
        String refreshToken = jwtUtil.generateRefreshToken(admin.getHotelId(), admin.getHotelId(), "ADMIN");

        // 4. Save New Session to Database
        AdminRefreshToken adminToken = AdminRefreshToken.builder()
                .userId(admin.getHotelId())
                .token(refreshToken)
                .expiryDate(LocalDateTime.now().plusDays(7))
                .build();

        adminRefreshTokenRepository.save(adminToken);

        return LoginResponse.builder()
                .message("Login successful")
                .adminId(admin.getId())
                .hotelId(admin.getHotelId())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    public Map<String, String> refreshAdminToken(String oldRefreshToken) {
        // 1. Basic Null Check & Physical Validation
        if (oldRefreshToken == null || !jwtUtil.validateToken(oldRefreshToken.trim())) {
            throw new RuntimeException("Refresh token is invalid or expired");
        }

        String cleanOldToken = oldRefreshToken.trim();

        // 2. THE OWNER'S CHECK (The Database Truth)
        // If you delete this from MongoDB, the user is KICKED OUT here.
        AdminRefreshToken storedToken = adminRefreshTokenRepository.findByToken(cleanOldToken)
                .orElseThrow(() -> new RuntimeException("Session has been revoked. Access Denied."));

        // 3. Extraction & Identity Verification
        String adminId = jwtUtil.extractUserId(cleanOldToken);
        String hotelId = jwtUtil.extractHotelId(cleanOldToken);
        String role = jwtUtil.extractRole(cleanOldToken);

        // Security Guard: Ensure the token being used actually belongs to the user in the DB record
        if (!storedToken.getUserId().equals(adminId)) {
            throw new RuntimeException("Identity mismatch. Security alert triggered.");
        }

        // --- 4. TOKEN ROTATION (Enterprise Standard) ---
        // We generate a NEW Access Token AND a NEW Refresh Token
        String newAccessToken = jwtUtil.generateAccessToken(adminId, hotelId, role);
        String newRefreshToken = jwtUtil.generateRefreshToken(adminId, hotelId, role);

        // 5. UPDATE the existing Database Record
        // We replace the old string with the new string.
        // This keeps the 'countByUserId' stable (doesn't increase session count).
        storedToken.setToken(newRefreshToken);
        storedToken.setExpiryDate(LocalDateTime.now().plusDays(7));
        adminRefreshTokenRepository.save(storedToken);

        // 6. Return the new pair to the Frontend
        return Map.of(
                "accessToken", newAccessToken,
                "refreshToken", newRefreshToken
        );
    }

    @Transactional
    public void logoutAdmin(String refreshToken) {
        // Trim the token to remove any accidental spaces/newlines from Postman
        Long deletedCount = adminRefreshTokenRepository.deleteByToken(refreshToken.trim());

        if (deletedCount == 0) {
            System.out.println("DEBUG: No token found in DB matching: " + refreshToken);
            // This confirms if the token was missing or the query failed
        } else {
            System.out.println("DEBUG: Successfully deleted " + deletedCount + " session(s)");
        }
    }
}