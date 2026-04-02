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

        if (!admin.isApproved()) throw new RuntimeException("Hotel not approved.");
        if (!admin.isActive()) throw new RuntimeException("Subscription inactive.");

        if (!passwordEncoder.matches(request.getPassword(), admin.getPassword())) {
            throw new RuntimeException("Invalid password");
        }

        // STRICT BLOCK LOGIC
        long activeSessions = adminRefreshTokenRepository.countByUserId(admin.getHotelId());
        int allowedLogins = (admin.getMaxLogins() > 0) ? admin.getMaxLogins() : 1;

        if (activeSessions >= allowedLogins) {
            throw new RuntimeException("Login limit reached (" + allowedLogins + "). Please logout elsewhere.");
        }

        // Generate Tokens (Version arguments removed)
        String accessToken = jwtUtil.generateAccessToken(admin.getHotelId(), admin.getHotelId(), "ADMIN");
        String refreshToken = jwtUtil.generateRefreshToken(admin.getHotelId(), admin.getHotelId(), "ADMIN");

        // Save New Session
        AdminRefreshToken adminToken = AdminRefreshToken.builder()
                .userId(admin.getHotelId())
                .token(refreshToken)
                .expiryDate(LocalDateTime.now().plusDays(7))
                .build();

        adminRefreshTokenRepository.save(adminToken);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    public Map<String, String> refreshAdminToken(String oldRefreshToken) {
        // 1. Find existing session by token string
        AdminRefreshToken storedToken = adminRefreshTokenRepository.findByToken(oldRefreshToken.trim())
                .orElseThrow(() -> new RuntimeException("Session revoked or invalid."));

        // 2. Extract identity
        String adminId = jwtUtil.extractUserId(oldRefreshToken);
        String hotelId = jwtUtil.extractHotelId(oldRefreshToken);
        String role = jwtUtil.extractRole(oldRefreshToken);

        // 3. Generate NEW pair (Version removed)
        String newAccess = jwtUtil.generateAccessToken(adminId, hotelId, role);
        String newRefresh = jwtUtil.generateRefreshToken(adminId, hotelId, role);

        // 4. UPDATE existing document (overwrites old token and expiry)
        storedToken.setToken(newRefresh);
        storedToken.setExpiryDate(LocalDateTime.now().plusDays(7));

        // This updates the existing record in MongoDB
        adminRefreshTokenRepository.save(storedToken);

        return Map.of(
                "accessToken", newAccess,
                "refreshToken", newRefresh
        );
    }

    @Transactional
    public void logoutAdmin(String refreshToken) {
        adminRefreshTokenRepository.deleteByToken(refreshToken.trim());
    }
}