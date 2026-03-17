package com.raghunath.hotelview.service.admin;

import com.raghunath.hotelview.dto.admin.LoginRequest;
import com.raghunath.hotelview.dto.admin.LoginResponse;
import com.raghunath.hotelview.entity.Admin;
import com.raghunath.hotelview.repository.AdminRepository;
import com.raghunath.hotelview.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final AdminRepository adminRepository;

    private final JwtUtil jwtUtil;

    private final BCryptPasswordEncoder passwordEncoder;

    public LoginResponse login(LoginRequest request) {

        Admin admin = adminRepository.findByMobile(request.getMobile())
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        if (!admin.isApproved()) {
            throw new RuntimeException("Hotel not approved. Contact Madhava Global.");
        }

        if (!admin.isActive()) {
            throw new RuntimeException("Subscription inactive. Contact Madhava Global.");
        }

        if (!passwordEncoder.matches(request.getPassword(), admin.getPassword())) {
            throw new RuntimeException("Invalid password");
        }

        String accessToken = jwtUtil.generateAccessToken(admin.getId());
        String refreshToken = jwtUtil.generateRefreshToken(admin.getId());

        return LoginResponse.builder()
                .message("Login successful")
                .adminId(admin.getId())
                .hotelId(admin.getHotelId())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }
}