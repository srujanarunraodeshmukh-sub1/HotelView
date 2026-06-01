package com.raghunath.hotelview.service.admin;

import com.raghunath.hotelview.dto.admin.*;
import com.raghunath.hotelview.entity.*;
import com.raghunath.hotelview.repository.*;
import com.raghunath.hotelview.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.FindAndModifyOptions;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final AdminRepository adminRepository;
    private final AdminRefreshTokenRepository adminRefreshTokenRepository;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder;
    private final PlanRepository planRepository;
    private final HotelViewDetailsService hotelViewDetailsService;
    private final CustomerDetailsRepository customerDetailsRepository;
    private final UserPaymentSubmissionRepository userPaymentSubmissionRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    private LocalDateTime getNowIST() {
        return ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).toLocalDateTime();
    }

    public LoginResponse login(LoginRequest request) {
        Admin admin = adminRepository.findByMobile(request.getMobile())
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        // 1. STEP ONE: Check Administrative Approval
        if (!admin.isApproved()) {
            throw new RuntimeException("Your account is not Approved. Contact Support Team!");
        }

        // 2. STEP TWO: Check Operational Active Status
        if (!admin.isActive()) {
            throw new RuntimeException("Your account is not currently Active. Contact Support Team");
        }

        // 3. STEP THREE: Password check (Only evaluated if account is approved and active)
        if (!passwordEncoder.matches(request.getPassword(), admin.getPassword())) {
            throw new RuntimeException("Invalid password");
        }

        // 4. STEP FOUR: Check Subscription Status (Sets flag, doesn't block access)
        boolean isExpired = getNowIST().isAfter(admin.getSubscriptionExpiry());

        // SMART SESSION MANAGEMENT
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
        LocalDateTime now = getNowIST();
        LocalDateTime subscriptionExpiry = now.plusDays(7);

        // 3. Generate Incremental Hotel ID using the sequence method
        String incrementalHotelId = generateIncrementalHotelId();

        // 4. Create and Save New Admin (Setting default states)
        Admin newAdmin = Admin.builder()
                .name(request.getName())
                .mobile(request.getMobile())
                .password(passwordEncoder.encode(request.getPassword()))
                .hotelId(incrementalHotelId)
                .isApproved(true)
                .isActive(true)
                .subscriptionStart(now)
                .subscriptionExpiry(subscriptionExpiry)
                .planType("Free Tier")
                .maxLogins(1)
                .build();

        Admin savedAdmin = adminRepository.save(newAdmin);

        return performLogin(savedAdmin, "Registration successful", false);
    }

    /**
     * Atomic sequence generator for Hotel IDs
     * Logic: Increments 'seq' in 'database_counters' collection
     */
    private String generateIncrementalHotelId() {
        Query query = new Query(Criteria.where("_id").is("hotel_id_sequence"));
        Update update = new Update().inc("seq", 1);

        DatabaseCounter counter = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true).upsert(true),
                DatabaseCounter.class
        );

        long sequence = (counter != null) ? counter.getSeq() : 1;

        return String.format("HOTEL%05d", sequence);
    }

    private LoginResponse performLogin(Admin admin, String message, boolean isExpired) {

        // FIXED: admin.getId() as subject, admin.getHotelId() as hotelId claim
        String accessToken = jwtUtil.generateAccessToken(
                admin.getId(),        // ← MongoDB _id as subject
                admin.getHotelId(),   // ← hotelId as claim
                "ADMIN"
        );
        String refreshToken = jwtUtil.generateRefreshToken(
                admin.getId(),        // ← MongoDB _id as subject
                admin.getHotelId(),   // ← hotelId as claim
                "ADMIN"
        );

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
                .isPlanExpired(isExpired)
                .expiryDate(admin.getSubscriptionExpiry() != null
                        ? admin.getSubscriptionExpiry()
                        .format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"))
                        : "N/A")
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
                .subscriptionExpiry(admin.getSubscriptionExpiry() != null
                        ? admin.getSubscriptionExpiry()
                        .format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                        : "N/A")
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

    public String submitPaymentProof(String url, String name, String address, String hotelId) {
        ZonedDateTime nowIST = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));

        UserPaymentSubmission submission = UserPaymentSubmission.builder()
                .hotelId(hotelId)
                .name(name)
                .address(address)
                .screenshotUrl(url)
                .submissionDate(nowIST.format(DateTimeFormatter.ISO_LOCAL_DATE))
                .submissionTime(nowIST.format(DateTimeFormatter.ofPattern("HH:mm:ss")))
                .createdAt(nowIST.toLocalDateTime()) // IST time
                .status("PENDING")
                .build();

        userPaymentSubmissionRepository.save(submission);

        return "congratulations you have successfully submitted your payment details we will shortly verify your details and upgrade your plan within few hours";
    }

    public Map<String, Object> getPaymentStatus(String hotelId) {

        Optional<UserPaymentSubmission> submission =
                userPaymentSubmissionRepository.findTopByHotelIdOrderByIdDesc(hotelId);

        if (submission.isEmpty()) {
            return Map.of(
                    "exists", false,
                    "message", "No payment submission found"
            );
        }

        UserPaymentSubmission s = submission.get();

        return Map.of(
                "exists", true,
                "hotelId", s.getHotelId() != null ? s.getHotelId() : "",
                "name", s.getName() != null ? s.getName() : "",
                "address", s.getAddress() != null ? s.getAddress() : "",
                "screenshotUrl", s.getScreenshotUrl() != null ? s.getScreenshotUrl() : "",
                "submissionDate", s.getSubmissionDate() != null ? s.getSubmissionDate() : "",
                "submissionTime", s.getSubmissionTime() != null ? s.getSubmissionTime() : "",
                "status", s.getStatus() != null ? s.getStatus() : "PENDING"
        );
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

        // 🚀 FIXED: Pass admin.getId() as the userId/Subject parameter instead of hotelId!
        String newAccess = jwtUtil.generateAccessToken(admin.getId(), hotelId, "ADMIN");
        String newRefresh = jwtUtil.generateRefreshToken(admin.getId(), hotelId, "ADMIN");

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

    public Map<String, Object> getSubscriptionDashboard(String hotelId) {
        // 1. Get Admin details
        Admin admin = adminRepository.findByHotelId(hotelId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        // 2. Fetch available plans
        List<Plan> allPlans = planRepository.findAll();
        Map<String, Object> response = new HashMap<>();

        // Core Dashboard Data
        response.put("currentPlan", admin.getPlanType());
        response.put("availableOptions", allPlans);

        // Adding ONLY the payment document data
        // We extract the 'payment_details' object specifically for the frontend
        org.bson.Document paymentDoc = hotelViewDetailsService.getPaymentInfo();

        if (paymentDoc != null) {
            response.put("paymentDetails", paymentDoc.get("payment_details"));
        }

        return response;
    }

    public Page<CustomerDetails> getCustomersByHotel(String hotelId, int page) {
        // Create Pageable: (page number, page size, sort direction, field name)
        Pageable pageable = PageRequest.of(page, 10, Sort.by(Sort.Direction.DESC, "lastOrderDate"));

        return customerDetailsRepository.findByHotelId(hotelId, pageable);
    }

    @Transactional
    public void logoutAdmin(String refreshToken) {
        adminRefreshTokenRepository.deleteByToken(refreshToken.trim());
    }
}