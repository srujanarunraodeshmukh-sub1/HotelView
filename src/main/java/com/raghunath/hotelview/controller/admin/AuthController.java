package com.raghunath.hotelview.controller.admin;

import com.raghunath.hotelview.dto.admin.*;
import com.raghunath.hotelview.service.admin.AdminAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AuthController {

    private final AdminAuthService adminAuthService;


    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {

        return adminAuthService.login(request);

    }

    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(adminAuthService.register(request));
    }

    @GetMapping("/profile")
    public ResponseEntity<AdminProfileDTO> getProfile() {
        String hotelId = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(adminAuthService.getProfile(hotelId));
    }

    @PutMapping("/profile")
    public ResponseEntity<String> updateProfile(@RequestBody AdminProfileDTO updates) {
        String hotelId = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(adminAuthService.updateProfile(hotelId, updates));
    }

    @GetMapping("/business")
    public ResponseEntity<BusinessDetailsDTO> getBusinessDetails() {
        String hotelId = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(adminAuthService.getBusinessDetails(hotelId));
    }

    @PutMapping("/business")
    public ResponseEntity<BusinessDetailsDTO> updateBusinessDetails(@RequestBody BusinessDetailsDTO updates) {
        String hotelId = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(adminAuthService.updateBusinessDetails(hotelId, updates));
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<Map<String, Object>> refresh(@RequestBody Map<String, String> request) {
        String oldRefreshToken = request.get("refreshToken");

        if (oldRefreshToken == null || oldRefreshToken.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Refresh token is required"));
        }

        // Change the variable type from Map<String, String> to Map<String, Object> 👇
        Map<String, Object> tokens = adminAuthService.refreshAdminToken(oldRefreshToken);

        return ResponseEntity.ok(tokens);
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(@RequestBody Map<String, String> request) {
        // Get the token from the body
        String refreshToken = request.get("refreshToken");

        if (refreshToken == null || refreshToken.isEmpty()) {
            return ResponseEntity.badRequest().body("Refresh token required for logout");
        }

        // Pass the TOKEN, not the ID
        adminAuthService.logoutAdmin(refreshToken);

        return ResponseEntity.ok("Admin logged out successfully");
    }
}