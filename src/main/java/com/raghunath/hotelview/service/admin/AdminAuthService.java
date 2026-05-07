package com.raghunath.hotelview.service.admin;

import com.raghunath.hotelview.dto.admin.*;
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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final AdminRepository adminRepository;
    private final AdminRefreshTokenRepository adminRefreshTokenRepository;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder;

    // Helper to get current time in IST
    private LocalDateTime getNowIST() {
        return ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).toLocalDateTime();
    }

    public LoginResponse login(LoginRequest request) {
        Admin admin = adminRepository.findByMobile(request.getMobile())
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        // 1. Password check
        if (!passwordEncoder.matches(request.getPassword(), admin.getPassword())) {
            throw new RuntimeException("Invalid password");
        }

        // 2. Check Subscription Status (Send as flag, don't block)
        boolean isExpired = getNowIST().isAfter(admin.getSubscriptionExpiry());

        // --- SMART SESSION MANAGEMENT ---
        List<AdminRefreshToken> activeSessions = adminRefreshTokenRepository.findByUserIdOrderByCreatedAtAsc(admin.getHotelId());
        int allowedLogins = (admin.getMaxLogins() > 0) ? admin.getMaxLogins() : 1;

        if (activeSessions.size() >= allowedLogins) {
            AdminRefreshToken oldestSession = activeSessions.get(0);
            adminRefreshTokenRepository.delete(oldestSession);
        }

        // Pass the isExpired flag to performLogin so it reaches the DTO
        return performLogin(admin, isExpired ? "Plan Expired. Read-only mode active." : "Login successful", isExpired);
    }

    public LoginResponse register(RegisterRequest request) {
        // 1. Validation
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("Passwords do not match!");
        }

        if (adminRepository.findByMobile(request.getMobile()).isPresent()) {
            throw new RuntimeException("Account already exists. Please login.");
        }

        // 2. Set Start and Expiry (IST)
        LocalDateTime now = getNowIST(); // Capture current time
        LocalDateTime subscriptionExpiry = now.plusDays(7);

        // 3. Create and Save New Admin
        Admin newAdmin = Admin.builder()
                .name(request.getName())
                .mobile(request.getMobile())
                .password(passwordEncoder.encode(request.getPassword()))
                .hotelId("HOTEL" + System.currentTimeMillis())
                .isApproved(true)
                .isActive(true)
                .subscriptionStart(now) // NEW FIELD ADDED HERE
                .subscriptionExpiry(subscriptionExpiry)
                .planType("Free Tier")
                .maxLogins(1)
                .build();

        Admin savedAdmin = adminRepository.save(newAdmin);

        // Registering users always get 'false' because their plan just started
        return performLogin(savedAdmin, "Registration successful", false);
    }

    // Shared logic for Login and Register to generate tokens
    // Add "boolean isExpired" as the 3rd parameter here 👇
    private LoginResponse performLogin(Admin admin, String message, boolean isExpired) {
        String accessToken = jwtUtil.generateAccessToken(admin.getHotelId(), admin.getHotelId(), "ADMIN");
        String refreshToken = jwtUtil.generateRefreshToken(admin.getHotelId(), admin.getHotelId(), "ADMIN");

        AdminRefreshToken adminToken = AdminRefreshToken.builder()
                .userId(admin.getHotelId())
                .token(refreshToken)
                .expiryDate(getNowIST().plusDays(7))
                .build();

        adminRefreshTokenRepository.save(adminToken);

        return LoginResponse.builder()
                .message(message)
                .adminId(admin.getId())
                .hotelId(admin.getHotelId())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .name(admin.getName())
                .mobile(admin.getMobile())
                .alternateMobile(admin.getAlternateMobile())
                .emailId(admin.getEmailId())
                .restaurantUpi(admin.getRestaurantUpi())
                .restaurantName(admin.getRestaurantName())
                .address(admin.getAddress())
                // Map the boolean flag to the DTO 👇
                .isPlanExpired(isExpired)
                .expiryDate(admin.getSubscriptionExpiry().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")))
                .build();
    }

    public AdminProfileDTO getProfile(String hotelId) {
        Admin admin = adminRepository.findByHotelId(hotelId)
                .orElseThrow(() -> new RuntimeException("Admin profile not found"));

        return AdminProfileDTO.builder()
                .name(admin.getName())
                .mobile(admin.getMobile())
                .alternateMobile(admin.getAlternateMobile())
                .emailId(admin.getEmailId())
                .restaurantUpi(admin.getRestaurantUpi())
                .address(admin.getAddress())
                .subscriptionExpiry(admin.getSubscriptionExpiry().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")))
                .build();
    }

    public String updateProfile(String hotelId, AdminProfileDTO updates) {
        Admin admin = adminRepository.findByHotelId(hotelId)
                .orElseThrow(() -> new RuntimeException("Admin profile not found"));

        admin.setName(updates.getName());
        admin.setAlternateMobile(updates.getAlternateMobile());
        admin.setEmailId(updates.getEmailId());
        admin.setRestaurantUpi(updates.getRestaurantUpi());
        admin.setAddress(updates.getAddress());

        adminRepository.save(admin);
        return "Profile updated successfully";
    }

    // Inside AdminService.java

    public BusinessDetailsDTO getBusinessDetails(String hotelId) {
        Admin admin = adminRepository.findByHotelId(hotelId)
                .orElseThrow(() -> new RuntimeException("Admin not found with ID: " + hotelId));

        return mapToDTO(admin);
    }

    public BusinessDetailsDTO updateBusinessDetails(String hotelId, BusinessDetailsDTO updates) {
        Admin admin = adminRepository.findByHotelId(hotelId)
                .orElseThrow(() -> new RuntimeException("Admin not found with ID: " + hotelId));

        // Update ONLY the specified business fields
        admin.setRestaurantName(updates.getRestaurantName());
        admin.setRestaurantAddress(updates.getRestaurantAddress());
        admin.setRestaurantContact(updates.getRestaurantContact());
        admin.setRestaurantLogo(updates.getRestaurantLogo());
        admin.setRestaurantUpi(updates.getRestaurantUpi());

        // planType is NOT updated here

        Admin savedAdmin = adminRepository.save(admin);
        return mapToDTO(savedAdmin);
    }

    private BusinessDetailsDTO mapToDTO(Admin admin) {
        return BusinessDetailsDTO.builder()
                .restaurantName(admin.getRestaurantName())
                .restaurantAddress(admin.getRestaurantAddress())
                .restaurantContact(admin.getRestaurantContact())
                .restaurantLogo(admin.getRestaurantLogo())
                .restaurantUpi(admin.getRestaurantUpi())
                .planType(admin.getPlanType())
                .build();
    }

    public Map<String, Object> refreshAdminToken(String oldRefreshToken) {
        AdminRefreshToken storedToken = adminRefreshTokenRepository.findByToken(oldRefreshToken.trim())
                .orElseThrow(() -> new RuntimeException("Session revoked or invalid."));

        String hotelId = jwtUtil.extractHotelId(oldRefreshToken);
        Admin admin = adminRepository.findByHotelId(hotelId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        // Check status for frontend visibility
        boolean isExpired = getNowIST().isAfter(admin.getSubscriptionExpiry());

        String newAccess = jwtUtil.generateAccessToken(hotelId, hotelId, "ADMIN");
        String newRefresh = jwtUtil.generateRefreshToken(hotelId, hotelId, "ADMIN");

        // Update existing document
        storedToken.setToken(newRefresh);
        storedToken.setExpiryDate(getNowIST().plusDays(7));
        adminRefreshTokenRepository.save(storedToken);

        // Return as Object map to include the boolean flag
        return Map.of(
                "accessToken", newAccess,
                "refreshToken", newRefresh,
                "isPlanExpired", isExpired
        );
    }

    // Inside AdminAuthService.java
    public void updatePlatformId(String hotelId, PlatformConfigRequest request) {
        Admin admin = adminRepository.findByHotelId(hotelId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        // 1. Initialize maps if they are null (safety check)
        if (admin.getPlatformIds() == null) admin.setPlatformIds(new HashMap<>());
        if (admin.getIntegrationStatus() == null) admin.setIntegrationStatus(new HashMap<>());

        String platform = request.getPlatformName().toUpperCase();

        // 2. Save the Merchant ID
        admin.getPlatformIds().put(platform, request.getMerchantId());

        // 3. Mark as Active
        admin.getIntegrationStatus().put(platform, true);

        adminRepository.save(admin);
    }

    @Transactional
    public void logoutAdmin(String refreshToken) {
        adminRefreshTokenRepository.deleteByToken(refreshToken.trim());
    }
}