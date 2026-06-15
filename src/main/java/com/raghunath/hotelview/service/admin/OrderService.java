package com.raghunath.hotelview.service.admin;

import com.raghunath.hotelview.dto.admin.*;
import com.raghunath.hotelview.entity.*;
import com.raghunath.hotelview.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderDraftRepository draftRepository;
    private final AdminRepository adminRepository;
    private final TableRepository tableRepository;
    private final EmployeeRepository employeeRepository;
    private final MenuItemRepository menuItemRepository;
    private final KitchenOrderRepository kitchenOrderRepository;
    private final MongoTemplate mongoTemplate;
    private final CompleteOrderRepository completeOrderRepository;
    private final VersionService versionService;
    private final ExternalOrderRepository externalOrderRepository;
    private final SalesAggregationRepository salesAggregationRepository;
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Autowired
    private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    /**
     * 1. SAVE DRAFT: Waiter/Admin adds items.
     */
    public void saveDraft(String hotelId, String tableName, List<OrderItem> items) {
        validateTableExists(hotelId, tableName);
        Double total = items.stream().mapToDouble(OrderItem::getSubTotal).sum();

        OrderDraft draft = draftRepository.findByHotelIdAndTableName(hotelId, tableName)
                .orElse(OrderDraft.builder()
                        .hotelId(hotelId)
                        .tableName(tableName)
                        .build());

        draft.setItems(items);
        draft.setTotalAmount(total);
        draft.setUpdatedAt(LocalDateTime.now());
        draftRepository.save(draft);
    }

    /**
     * 2. FETCH DRAFT: View current unsent items.
     */
    public OrderDraft getDraft(String hotelId, String tableName) {
        return draftRepository.findByHotelIdAndTableName(hotelId, tableName).orElse(null);
    }

    /**
     * 3. CONFIRM ORDER: Moves items to the Kitchen.
     */
    @Transactional
    public String confirmOrder(String hotelId, String tableName, List<OrderItem> items, String waiterId, String comment) {
        // 1. Subscription Check
        Admin admin = adminRepository.findByHotelId(hotelId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        if (getISTNow().toLocalDateTime().isAfter(admin.getSubscriptionExpiry())) {
            throw new RuntimeException("Kindly upgrade to the Standard or Premium plan.");
        }

        // 2. Original Logic
        validateTableExists(hotelId, tableName);
        ZonedDateTime nowIST = getISTNow();
        Double total = items.stream().mapToDouble(OrderItem::getSubTotal).sum();

        KitchenOrder kOrder = KitchenOrder.builder()
                .hotelId(hotelId)
                .tableName(tableName)
                .orderType("TABLE")
                .items(items)
                .totalAmount(total)
                .status("PENDING")
                .comments(comment)
                .placedBy(waiterId)
                .createdDate(nowIST.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                .createdTime(nowIST.format(DateTimeFormatter.ofPattern("HH:mm:ss")))
                .build();

        kitchenOrderRepository.save(kOrder);
        draftRepository.findByHotelIdAndTableName(hotelId, tableName).ifPresent(draftRepository::delete);

        updateTableVisualStatus(hotelId, tableName, "PENDING");
        updateTableBill(hotelId, tableName, total, false);

        return "Order sent to kitchen";
    }

    @Transactional
    public String confirmCustomItemOrder(String hotelId, CustomKitchenOrderRequest request, String waiterId) {
        // 1. Subscription Check (Mirrors original logic perfectly)
        Admin admin = adminRepository.findByHotelId(hotelId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        if (getISTNow().toLocalDateTime().isAfter(admin.getSubscriptionExpiry())) {
            throw new RuntimeException("Kindly upgrade to the Standard or Premium plan.");
        }

        // 2. Fallback Defaults & Dynamic Validation
        String tableName = org.springframework.util.StringUtils.hasText(request.getTableName()) ? request.getTableName() : "Counter";
        String orderType = org.springframework.util.StringUtils.hasText(request.getOrderType()) ? request.getOrderType() : "TABLE";

        if (orderType.equals("TABLE")) {
            validateTableExists(hotelId, tableName);
        }

        if (!org.springframework.util.StringUtils.hasText(request.getItemName())) {
            throw new RuntimeException("Custom item name cannot be empty");
        }

        Double total = (request.getSubTotal() != null) ? request.getSubTotal() : 0.0;
        ZonedDateTime nowIST = getISTNow();

        // 3. Build the OrderItem DTO dynamically on-the-fly
        // Note: Converted quantity integer to String to prevent type casting bugs on your schema!
        OrderItem customItem = OrderItem.builder()
                .itemName(request.getItemName())
                .quantity(request.getQuantity() != null ? String.valueOf(request.getQuantity()) : "1")
                .price(request.getQuantity() != null && request.getQuantity() > 0 ? total / request.getQuantity() : total)
                .subTotal(total)
                .build();

        List<OrderItem> itemsList = new ArrayList<>();
        itemsList.add(customItem);

        // 4. Construct KitchenOrder entity matching image_c49357.png
        KitchenOrder kOrder = KitchenOrder.builder()
                .hotelId(hotelId)
                .tableName(tableName)
                .orderType(orderType)
                .items(itemsList)
                .totalAmount(total)
                .status("PENDING")
                .comments(request.getComment() != null ? request.getComment() : "")
                .placedBy(waiterId)
                .createdDate(nowIST.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                .createdTime(nowIST.format(DateTimeFormatter.ofPattern("HH:mm:ss")))
                .build();

        // 5. Database Save & Visual Sync Operations
        kitchenOrderRepository.save(kOrder);

        // Clear layout drafts if they exist for this section
        draftRepository.findByHotelIdAndTableName(hotelId, tableName).ifPresent(draftRepository::delete);

        // Update real-time metrics across your multi-tenant layouts
        if (orderType.equals("TABLE")) {
            updateTableVisualStatus(hotelId, tableName, "PENDING");
            updateTableBill(hotelId, tableName, total, false);
        }

        return "Custom item order sent to kitchen successfully";
    }

    private void validateTableExists(String hotelId, String tableName) {
        if (!tableRepository.existsByHotelIdAndTableName(hotelId, tableName)) {
            log.error("VALIDATION_FAILED: Table {} not found for Hotel {}", tableName, hotelId);
            throw new RuntimeException("Invalid Table Number: " + tableName);
        }
    }

    /**
     * 4. CONFIRM HOME DELIVERY ORDER: Moves items to the Kitchen.
     */
    @Transactional
    public String confirmHomeDelivery(String hotelId, List<OrderItem> items, String waiterId, String orderType) {
        // 1. Subscription Check
        Admin admin = adminRepository.findByHotelId(hotelId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        if (getISTNow().toLocalDateTime().isAfter(admin.getSubscriptionExpiry())) {
            throw new RuntimeException("Kindly upgrade to the Standard or Premium plan.");
        }

        // 2. Original Logic
        ZonedDateTime nowIST = getISTNow();
        Double total = items.stream().mapToDouble(OrderItem::getSubTotal).sum();

        KitchenOrder deliveryOrder = KitchenOrder.builder()
                .hotelId(hotelId)
                .tableName(null)
                .orderType(orderType.toUpperCase())
                .items(items)
                .totalAmount(total)
                .status("PENDING")
                .placedBy(waiterId)
                .createdDate(nowIST.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                .createdTime(nowIST.format(DateTimeFormatter.ofPattern("HH:mm:ss")))
                .build();

        String savedId = kitchenOrderRepository.save(deliveryOrder).getId();
        versionService.bumpTables(hotelId);

        return savedId;
    }

    /**
     * 5. FETCH TABLE ORDERS: Latest orders first.
     */
    public List<KitchenOrder> getOrdersByTable(String hotelId, String tableName) {
        return kitchenOrderRepository.findByHotelIdAndTableNameAndStatusNotOrderByCreatedDateDescCreatedTimeDesc(
                hotelId, tableName, "PAID");
    }

    /**
     * 6. CHECKOUT ORDERS: checkout all orders.
     */
    @CacheEvict(value = "dashboardStatsCache", key = "#hotelId")
    @Transactional
    public CheckoutResponse checkoutOrders(String hotelId, CheckoutRequest request, String checkoutBy) {
        // 1. Subscription Check
        Admin admin = adminRepository.findByHotelId(hotelId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        if (getISTNow().toLocalDateTime().isAfter(admin.getSubscriptionExpiry())) {
            throw new RuntimeException("Your subscription plan has ended.");
        }

        // 2. Fetch Active Orders
        List<KitchenOrder> activeOrders = kitchenOrderRepository.findAllById(request.getOrderIds());
        if (activeOrders.isEmpty()) {
            throw new RuntimeException("No active orders found");
        }
        String tableName = activeOrders.get(0).getTableName();

        // 3. Aggregate Items
        Map<String, CheckoutResponse.BillItem> itemMap = new HashMap<>();
        for (KitchenOrder order : activeOrders) {
            order.getItems().forEach(item -> {
                itemMap.merge(item.getItemName(),
                        CheckoutResponse.BillItem.builder()
                                .itemName(item.getItemName())
                                .quantity(item.getQuantity())
                                .price(item.getSubTotal())
                                .build(),
                        (oldVal, newVal) -> {
                            oldVal.setQuantity(newVal.getQuantity());
                            oldVal.setPrice(oldVal.getPrice() + newVal.getPrice());
                            return oldVal;
                        });
            });
        }

        Double grandTotal = activeOrders.stream().mapToDouble(KitchenOrder::getTotalAmount).sum();
        Double discountPercent = (request.getDiscount() != null) ? request.getDiscount() : 0.0;
        Double discountAmount = (grandTotal * discountPercent) / 100;
        Double totalPayable = grandTotal - discountAmount;

        ZonedDateTime nowIST = getISTNow();

        // 4. Build and Save Completed Order
        CompletedOrder finalBill = CompletedOrder.builder()
                .hotelId(hotelId)
                .orderType(activeOrders.get(0).getOrderType())
                .tableName(tableName)
                .customerName(StringUtils.hasText(request.getCustomerName()) ? request.getCustomerName() : "Walk-in Guest")
                .customerMobile(StringUtils.hasText(request.getCustomerMobile()) ? request.getCustomerMobile() : "0000000000")
                .customerAddress(StringUtils.hasText(request.getCustomerAddress()) ? request.getCustomerAddress() : "N/A")
                .allOrders(activeOrders)
                .grandTotal(grandTotal)
                .paymentStatus("PAID")
                .discountPercent(discountPercent)
                .discountAmount(discountAmount)
                .totalPayable(totalPayable)
                .checkoutBy(checkoutBy)
                .checkoutDate(nowIST.format(DateTimeFormatter.ISO_LOCAL_DATE))
                .checkoutTime(nowIST.format(DateTimeFormatter.ofPattern("HH:mm:ss")))
                .build();

        CompletedOrder savedBill = completeOrderRepository.save(finalBill);

        // 5. Cleanup and Sync
        if (savedBill.getId() != null) {
            syncCustomerDetails(hotelId, finalBill);
            kitchenOrderRepository.deleteAll(activeOrders);
            updateTableBill(hotelId, tableName, 0.0, true);  // ← ADD THIS
        }

        // 6. Build Response
        String fullId = savedBill.getId();
        String shortId = (fullId != null && fullId.length() > 6) ? fullId.substring(fullId.length() - 6) : fullId;

        return CheckoutResponse.builder()
                .id(fullId)
                .shortId(shortId)
                .checkoutDate(finalBill.getCheckoutDate())
                .checkoutTime(finalBill.getCheckoutTime())
                .orderType(finalBill.getOrderType())
                .tableName(finalBill.getTableName())
                .customerName(finalBill.getCustomerName())
                .customerMobile(finalBill.getCustomerMobile())
                .customerAddress(finalBill.getCustomerAddress())
                .items(new ArrayList<>(itemMap.values()))
                .grandTotal(grandTotal)
                .discountPercent(discountPercent)
                .discountAmount(discountAmount)
                .totalPayable(totalPayable)
                .restaurantName(admin.getRestaurantName())        // ← ADD
                .restaurantAddress(admin.getRestaurantAddress())  // ← ADD
                .restaurantContact(admin.getRestaurantContact())  // ← ADD
                .restaurantUpi(admin.getRestaurantUpi())          // ← ADD
                .restaurantLogo(admin.getRestaurantLogo())
                .build();
    }

    /**
     * 7. INSTANT CHECKOUT
     */
    @CacheEvict(value = "dashboardStatsCache", key = "#hotelId")
    @Transactional
    public CheckoutResponse instantOrderAndCheckout(String hotelId, InstantCheckoutRequest request, String actualLoggedInUser) {
        // 1. Subscription Check
        Admin admin = adminRepository.findByHotelId(hotelId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        if (getISTNow().toLocalDateTime().isAfter(admin.getSubscriptionExpiry())) {
            throw new RuntimeException("Kindly upgrade to the Standard or Premium plan.");
        }

        List<OrderItem> items = request.getItems();
        Double grandTotal = items.stream().mapToDouble(OrderItem::getSubTotal).sum();

        Double discountPercent = request.getDiscountPercent() != null ? request.getDiscountPercent() : 0.0;
        Double discountAmount = (grandTotal * discountPercent) / 100.0;
        Double totalPayable = Math.max(0.0, grandTotal - discountAmount);

        ZonedDateTime nowIST = getISTNow();

        // 2. IN-MEMORY TRANSIENT OBJECT
        KitchenOrder transientOrder = KitchenOrder.builder()
                .items(items)
                .build();

        // 3. Prepare Bill Items
        List<CheckoutResponse.BillItem> billItems = items.stream()
                .map(item -> CheckoutResponse.BillItem.builder()
                        .itemName(item.getItemName())
                        .quantity(item.getQuantity())
                        .price(item.getSubTotal())
                        .build())
                .toList();

        // 4. Construct and Save CompletedOrder
        CompletedOrder finalBill = CompletedOrder.builder()
                .hotelId(hotelId)
                .orderType("INSTANT")
                .customerName(null)
                .customerMobile(null)
                .customerAddress(null)
                .allOrders(Collections.singletonList(transientOrder))
                .grandTotal(grandTotal)
                .paymentStatus("PAID")
                .discountPercent(discountPercent)
                .discountAmount(discountAmount)
                .totalPayable(totalPayable)
                .checkoutBy(actualLoggedInUser)
                .checkoutDate(nowIST.format(DateTimeFormatter.ISO_LOCAL_DATE))
                .checkoutTime(nowIST.format(DateTimeFormatter.ofPattern("HH:mm:ss")))
                .build();

        CompletedOrder savedBill = completeOrderRepository.save(finalBill);

        // 5. Build Response
        String fullId = savedBill.getId();
        String shortId = (fullId != null && fullId.length() > 6) ? fullId.substring(fullId.length() - 6) : fullId;

        return CheckoutResponse.builder()
                .id(fullId)
                .shortId(shortId)
                .checkoutDate(finalBill.getCheckoutDate())
                .checkoutTime(finalBill.getCheckoutTime())
                .orderType("INSTANT")
                .items(billItems)
                .grandTotal(grandTotal)
                .totalPayable(totalPayable)
                .build();
    }

    @CacheEvict(value = "dashboardStatsCache", key = "#hotelId")
    @Transactional
    public CheckoutResponse instantCustomOrderAndCheckout(String hotelId, InstantCheckoutRequestNew request, String actualLoggedInUser) {
        // 1. Subscription Plan Validation
        Admin admin = adminRepository.findByHotelId(hotelId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        if (getISTNow().toLocalDateTime().isAfter(admin.getSubscriptionExpiry())) {
            throw new RuntimeException("Kindly upgrade to the Standard or Premium plan.");
        }

        List<OrderItem> items = request.getItems();
        if (items == null || items.isEmpty()) {
            throw new RuntimeException("Cannot process an instant checkout with an empty items list");
        }

        // Mathematical Computations
        Double grandTotal = items.stream().mapToDouble(OrderItem::getSubTotal).sum();
        Double discountPercent = request.getDiscountPercent() != null ? request.getDiscountPercent() : 0.0;
        Double discountAmount = (grandTotal * discountPercent) / 100.0;
        Double totalPayable = Math.max(0.0, grandTotal - discountAmount);

        ZonedDateTime nowIST = getISTNow();
        String currentDateStr = nowIST.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String currentTimeStr = nowIST.format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        // 2. Build Sanitized In-Memory OrderItems List (Ensuring clean Strings)
        List<OrderItem> sanitizedItems = items.stream().map(item -> {
            String qtyStr = item.getQuantity() != null ? String.valueOf(item.getQuantity()).trim() : "1";
            return OrderItem.builder()
                    .itemName(item.getItemName())
                    .quantity(qtyStr)
                    .price(item.getPrice() != null ? item.getPrice() : 0.0)
                    .subTotal(item.getSubTotal())
                    .build();
        }).toList();

        // 3. Construct the Transient KitchenOrder Document Wrapper
        KitchenOrder transientOrder = KitchenOrder.builder()
                .id(new org.bson.types.ObjectId().toString())
                .hotelId(hotelId)
                .tableName(null) // Matching production null states
                .orderType("INSTANT")
                .items(sanitizedItems)
                .totalAmount(grandTotal)
                .status("COMPLETED")
                .placedBy(actualLoggedInUser)
                .completedBy(actualLoggedInUser)
                .updatedAt(nowIST.toLocalDateTime())
                .createdDate(currentDateStr)
                .createdTime(currentTimeStr)
                .build();

        // 4. Map Response Items (Matching your exact production output model layouts)
        List<CheckoutResponse.BillItem> billItems = sanitizedItems.stream()
                .map(item -> CheckoutResponse.BillItem.builder()
                        .itemName(item.getItemName())
                        .quantity(item.getQuantity()) // Keep as String matching production
                        .price(item.getSubTotal())   // Matches production layout (quantity * unit price)
                        .build())
                .toList();

        // 5. Construct and Save Final CompletedOrder Collection Entry (Enforcing exact production nulls)
        CompletedOrder finalBill = CompletedOrder.builder()
                .hotelId(hotelId)
                .orderType("INSTANT")
                .customerName(null)
                .customerMobile(null)
                .customerAddress(null)
                .allOrders(Collections.singletonList(transientOrder))
                .grandTotal(grandTotal)
                .paymentStatus("PAID")
                .discountPercent(discountPercent)
                .discountAmount(discountAmount)
                .totalPayable(totalPayable)
                .checkoutBy(actualLoggedInUser)
                .checkoutDate(currentDateStr)
                .checkoutTime(currentTimeStr)
                .build();

        CompletedOrder savedBill = completeOrderRepository.save(finalBill);

        // 6. Build & Return Standardized CheckoutResponse Payload (Cleaned up to match standard logging)
        String fullId = savedBill.getId();
        String shortId = (fullId != null && fullId.length() > 6) ? fullId.substring(fullId.length() - 6) : fullId;

        return CheckoutResponse.builder()
                .id(fullId)
                .shortId(shortId)
                .checkoutDate(finalBill.getCheckoutDate())
                .checkoutTime(finalBill.getCheckoutTime())
                .orderType("INSTANT")
                .items(billItems)
                .grandTotal(grandTotal)
                .totalPayable(totalPayable)
                .build();
    }

    @CacheEvict(value = "dashboardStatsCache", key = "#hotelId")
    @Transactional
    public CheckoutResponse checkoutDirectOrder(String hotelId, DirectOrderRequest request, String checkoutBy) {
        // 1. Subscription Check (Mirroring standard checkouts)
        Admin admin = adminRepository.findByHotelId(hotelId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        if (getISTNow().toLocalDateTime().isAfter(admin.getSubscriptionExpiry())) {
            throw new RuntimeException("Your subscription plan has ended.");
        }

        // 2. Validate Direct Request Metrics
        if (request.getTotalAmount() == null || request.getTotalAmount() <= 0) {
            throw new RuntimeException("Invalid total amount for direct checkout");
        }
        if (!org.springframework.util.StringUtils.hasText(request.getItemName())) {
            throw new RuntimeException("Item name cannot be empty for manual logging");
        }

        Double grandTotal = request.getTotalAmount();
        Double discountPercent = (request.getDiscount() != null) ? request.getDiscount() : 0.0;
        Double discountAmount = (grandTotal * discountPercent) / 100;
        Double totalPayable = grandTotal - discountAmount;

        ZonedDateTime nowIST = getISTNow();
        String currentDateStr = nowIST.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String currentTimeStr = nowIST.format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        // 3. Construct Raw Items Array List to match your standard inner model formats
        List<com.raghunath.hotelview.dto.admin.OrderItem> inlineItems = new ArrayList<>();

        // Use the exact setter or builder pattern that your OrderItem class provides
        inlineItems.add(com.raghunath.hotelview.dto.admin.OrderItem.builder()
                .itemName(request.getItemName())
                .quantity(request.getQuantity() != null ? String.valueOf(request.getQuantity()) : "1")
                .price(0.0) // Kept at zero or null per requirement
                .subTotal(grandTotal)
                .build());

        // 4. Construct Nested KitchenOrder Object Structure matching standard schema exactly
        KitchenOrder mockOrder = KitchenOrder.builder()
                .id(new org.bson.types.ObjectId().toString())
                .hotelId(hotelId)
                .tableName(org.springframework.util.StringUtils.hasText(request.getTableName()) ? request.getTableName() : "Counter")
                .orderType(org.springframework.util.StringUtils.hasText(request.getOrderType()) ? request.getOrderType() : "TAKEAWAY")
                .items(inlineItems)
                .totalAmount(grandTotal)
                .status("COMPLETED")
                .placedBy(checkoutBy)
                .completedBy(checkoutBy)
                .updatedAt(nowIST.toLocalDateTime())
                .createdDate(currentDateStr)
                .createdTime(currentTimeStr)
                .build();

        List<KitchenOrder> allOrdersList = new ArrayList<>();
        allOrdersList.add(mockOrder);

        // 5. Build and Save Completed Order
        CompletedOrder finalBill = CompletedOrder.builder()
                .hotelId(hotelId)
                .orderType(mockOrder.getOrderType())
                .tableName(mockOrder.getTableName())
                .customerName(org.springframework.util.StringUtils.hasText(request.getCustomerName()) ? request.getCustomerName() : "Walk-in Guest")
                .customerMobile(org.springframework.util.StringUtils.hasText(request.getCustomerMobile()) ? request.getCustomerMobile() : "0000000000")
                .customerAddress(org.springframework.util.StringUtils.hasText(request.getCustomerAddress()) ? request.getCustomerAddress() : "N/A")
                .allOrders(allOrdersList)
                .grandTotal(grandTotal)
                .paymentStatus("PAID")
                .discountPercent(discountPercent)
                .discountAmount(discountAmount)
                .totalPayable(totalPayable)
                .checkoutBy(checkoutBy)
                .checkoutDate(currentDateStr)
                .checkoutTime(currentTimeStr)
                .build();

        CompletedOrder savedBill = completeOrderRepository.save(finalBill);

        // 6. Cleanup and Sync
        if (savedBill.getId() != null) {
            syncCustomerDetails(hotelId, finalBill);
            if (org.springframework.util.StringUtils.hasText(request.getTableName())) {
                updateTableBill(hotelId, request.getTableName(), 0.0, true);
            }
        }

        // 7. Build Uniform Response Object
        String fullId = savedBill.getId();
        String shortId = (fullId != null && fullId.length() > 6) ? fullId.substring(fullId.length() - 6) : fullId;

        // Convert string quantity safely into integer to prevent type conversion errors during response building
        int parsedQuantity = 1;
        try {
            parsedQuantity = Integer.parseInt(mockOrder.getItems().get(0).getQuantity());
        } catch (NumberFormatException e) {
            // Fallback safety parameter if a dirty string passes through
            if (request.getQuantity() != null) {
                parsedQuantity = request.getQuantity();
            }
        }

        List<CheckoutResponse.BillItem> responseItems = new ArrayList<>();
        responseItems.add(CheckoutResponse.BillItem.builder()
                .itemName(request.getItemName())
                .quantity(String.valueOf(parsedQuantity)) // ✔️ Cleanly casted primitive int value applied here
                .price(grandTotal)
                .build());

        return CheckoutResponse.builder()
                .id(fullId)
                .shortId(shortId)
                .checkoutDate(finalBill.getCheckoutDate())
                .checkoutTime(finalBill.getCheckoutTime())
                .orderType(finalBill.getOrderType())
                .tableName(finalBill.getTableName())
                .customerName(finalBill.getCustomerName())
                .customerMobile(finalBill.getCustomerMobile())
                .customerAddress(finalBill.getCustomerAddress())
                .items(responseItems)
                .grandTotal(grandTotal)
                .totalPayable(totalPayable)
                .restaurantName(admin.getRestaurantName())
                .restaurantAddress(admin.getRestaurantAddress())
                .restaurantContact(admin.getRestaurantContact())
                .restaurantUpi(admin.getRestaurantUpi())
                .restaurantLogo(admin.getRestaurantLogo())
                .build();
    }

    /**
     * 8. FETCH DASHBOARD STATS
     */
    @Cacheable(value = "dashboardStatsCache", key = "#hotelId")
    public DashboardStatsDTO getDashboardStats(String hotelId) {
        ZonedDateTime nowIST = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
        String todayDate = nowIST.format(DateTimeFormatter.ISO_LOCAL_DATE);

        Admin admin = adminRepository.findByHotelId(hotelId)
                .orElseThrow(() -> new RuntimeException("Hotel Admin not found"));

        ZonedDateTime last24HoursStart = nowIST.minusHours(24);
        String last24HoursStartDate = last24HoursStart.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String last24HoursStartTime = last24HoursStart.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String nowTime = nowIST.format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        String restaurantName = admin.getRestaurantName() != null ? admin.getRestaurantName() : "Unknown Restaurant";
        String planType = admin.getPlanType() != null ? admin.getPlanType() : "BASIC";

        List<String> activeStatuses = List.of("PENDING", "ACCEPTED", "ACTIVE");
        Long activeTablesCount = tableRepository.countByHotelIdAndStatusIn(hotelId, activeStatuses);

        List<String> deliveryTypes = List.of("HOME", "PARCEL");
        Long homeAndParcelOrdersToday = completeOrderRepository.countByHotelIdAndOrderTypeInAndCheckoutDate(
                hotelId, deliveryTypes, todayDate);

        List<String> deliveryType = List.of("INSTANT");
        Long instantOrdersToday = completeOrderRepository.countByHotelIdAndOrderTypeInAndCheckoutDate(hotelId, deliveryType, todayDate);

        Long employeeCount = employeeRepository.countByHotelIdAndIsActive(hotelId, true);
        Long totalItems = menuItemRepository.countByHotelId(hotelId);
        Long completedTodayCount = completeOrderRepository.countByHotelIdAndCheckoutDate(hotelId, todayDate);

        Double todaySalesRupees = 0.0;
        try {
            Double result = completeOrderRepository.sumTotalPayableByHotelIdAndCheckoutDate(hotelId, todayDate);
            todaySalesRupees = (result != null) ? result : 0.0;
        } catch (Exception e) {
            log.error("AGGREGATION_ERROR: Sales sum failed for hotel {}", hotelId);
        }

        Double last24HoursSales = 0.0;
        Long last24HoursOrders = 0L;
        try {
            List<CompletedOrder> last24HoursCompletedOrders = mongoTemplate.find(
                    new Query(Criteria.where("hotelId").is(hotelId)
                            .orOperator(
                                    Criteria.where("checkoutDate").is(todayDate),
                                    Criteria.where("checkoutDate").is(last24HoursStartDate)
                            )),
                    CompletedOrder.class
            );

            last24HoursOrders = last24HoursCompletedOrders.stream()
                    .filter(o -> isWithinLast24Hours(o, last24HoursStart, nowIST))
                    .count();

            last24HoursSales = last24HoursCompletedOrders.stream()
                    .filter(o -> isWithinLast24Hours(o, last24HoursStart, nowIST))
                    .mapToDouble(o -> o.getTotalPayable() != null ? o.getTotalPayable() : 0.0)
                    .sum();
        } catch (Exception e) {
            log.error("AGGREGATION_ERROR: Last 24h stats failed for hotel {}", hotelId);
        }

        return DashboardStatsDTO.builder()
                .activeTablesCount(activeTablesCount)
                .HomeDeliveriesCount(homeAndParcelOrdersToday)
                .InstantCount(instantOrdersToday)
                .completedOrdersTodayCount(completedTodayCount)
                .employeeOnlineCount(employeeCount)
                .totalItemsCount(totalItems)
                .restaurantName(restaurantName)
                .planType(planType)
                .todaySalesRupees(todaySalesRupees)
                .last24HoursSalesRupees(last24HoursSales)
                .last24HoursOrdersCount(last24HoursOrders)
                .restaurantAddress(admin.getRestaurantAddress())    // ← ADD
                .restaurantContact(admin.getRestaurantContact())    // ← ADD
                .restaurantUpi(admin.getRestaurantUpi())
                .restaurantLogo(admin.getRestaurantLogo())
                .build();
    }

    private boolean isWithinLast24Hours(CompletedOrder order, ZonedDateTime from, ZonedDateTime to) {
        try {
            // Combine checkoutDate + checkoutTime into a ZonedDateTime for comparison
            LocalDateTime orderDateTime = LocalDateTime.parse(
                    order.getCheckoutDate() + "T" + order.getCheckoutTime()
            );
            ZonedDateTime orderZDT = orderDateTime.atZone(IST);
            return !orderZDT.isBefore(from) && !orderZDT.isAfter(to);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 9. EDIT ORDER
     */
    @Transactional
    public void confirmOrderEdit(String hotelId, String orderId, List<OrderItem> newItems, String editedBy) {
        KitchenOrder order = kitchenOrderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        List<OrderEdit> editLogs = new ArrayList<>();
        String timeIST = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        for (OrderItem oldItem : order.getItems()) {
            OrderItem newItem = newItems.stream()
                    .filter(ni -> ni.getItemName().equalsIgnoreCase(oldItem.getItemName()))
                    .findFirst()
                    .orElse(null);

            String oldQtyStr = (oldItem.getQuantity() != null) ? oldItem.getQuantity() : "0";
            String newQtyStr = (newItem != null && newItem.getQuantity() != null) ? newItem.getQuantity() : "0";

            if (!oldQtyStr.equalsIgnoreCase(newQtyStr)) {
                editLogs.add(OrderEdit.builder()
                        .orderId(orderId)
                        .hotelId(hotelId)
                        .editedBy(editedBy)
                        .itemName(oldItem.getItemName())
                        .previousQty(Integer.parseInt(oldQtyStr.replaceAll("[^0-9]", "")))
                        .newQty(Integer.parseInt(newQtyStr.replaceAll("[^0-9]", "")))
                        .delta(0)
                        .timestamp(timeIST)
                        .build());
            }
        }

        for (OrderItem newItem : newItems) {
            boolean exists = order.getItems().stream()
                    .anyMatch(old -> old.getItemName().equalsIgnoreCase(newItem.getItemName()));

            if (!exists) {
                String newQtyStr = (newItem.getQuantity() != null) ? newItem.getQuantity() : "0";

                editLogs.add(OrderEdit.builder()
                        .orderId(orderId)
                        .hotelId(hotelId)
                        .editedBy(editedBy)
                        .itemName(newItem.getItemName())
                        .previousQty(0)
                        .newQty(Integer.parseInt(newQtyStr.replaceAll("[^0-9]", "")))
                        .delta(0)
                        .timestamp(timeIST)
                        .build());
            }
        }

        double oldTotal = order.getTotalAmount();
        double newTotal = newItems.stream().mapToDouble(OrderItem::getSubTotal).sum();

        order.setItems(newItems);
        order.setTotalAmount(newTotal);
        kitchenOrderRepository.save(order);

        updateTableBill(hotelId, order.getTableName(), (newTotal - oldTotal), false);

        if (!editLogs.isEmpty()) {
            mongoTemplate.insert(editLogs, "order_edits");
        }

        versionService.bumpTables(hotelId);
    }

    /**
     * 10. Get Full Table History via Completed Order ID
     */
    public Map<String, Object> getAggregatedTableSummary(String hotelId, String completedOrderId) {
        CompletedOrder completedBill = mongoTemplate.findById(completedOrderId, CompletedOrder.class);

        if (completedBill == null) {
            throw new RuntimeException("Completed Order record not found");
        }

        Map<String, Object> finalTableHistory = new LinkedHashMap<>();

        for (KitchenOrder subOrder : completedBill.getAllOrders()) {
            String subOrderId = subOrder.getId();

            Query query = new Query(Criteria.where("orderId").is(subOrderId).and("hotelId").is(hotelId));
            query.with(Sort.by(Sort.Direction.ASC, "timestamp"));

            List<OrderEdit> editHistory = mongoTemplate.find(query, OrderEdit.class, "order_edits");

            Map<String, Object> orderEntry = new LinkedHashMap<>();
            orderEntry.put("orderType", subOrder.getOrderType());
            orderEntry.put("finalItems", subOrder.getItems());
            orderEntry.put("totalAmount", subOrder.getTotalAmount());

            // Resolve names for the sub-order lifecycles instead of returning raw Hex IDs
            orderEntry.put("placedBy", getUserRealName(subOrder.getPlacedBy()));
            orderEntry.put("acceptedBy", getUserRealName(subOrder.getAcceptedBy()));
            orderEntry.put("completedBy", getUserRealName(subOrder.getCompletedBy()));

            if (editHistory.isEmpty()) {
                orderEntry.put("editSummary", "No order edit summary available");
            } else {
                // Transform edit history maps dynamically to swap "editedBy" IDs for names
                List<Map<String, Object>> dynamicEditSummaryList = new ArrayList<>();
                for (OrderEdit edit : editHistory) {
                    Map<String, Object> editMap = new LinkedHashMap<>();
                    editMap.put("id", edit.getId());
                    editMap.put("orderId", edit.getOrderId());
                    editMap.put("hotelId", edit.getHotelId());

                    // Resolve the dynamic editor ID into their plain text name string here!
                    editMap.put("editedBy", getUserRealName(edit.getEditedBy()));

                    editMap.put("itemName", edit.getItemName());
                    editMap.put("previousQty", edit.getPreviousQty());
                    editMap.put("newQty", edit.getNewQty());
                    editMap.put("delta", edit.getDelta());
                    editMap.put("timestamp", edit.getTimestamp());

                    dynamicEditSummaryList.add(editMap);
                }
                orderEntry.put("editSummary", dynamicEditSummaryList);
            }

            finalTableHistory.put("Order_" + subOrderId, orderEntry);
        }

        // Resolve checkout context identity name at root level termination
        finalTableHistory.put("checkoutBy", getUserRealName(completedBill.getCheckoutBy()));

        return finalTableHistory;
    }

    /**
     * Helper method to perform cross-collection name resolution for dynamic user IDs.
     * Searches the Employee collection first, falls back to Admin, then defaults to the ID.
     */
    private String getUserRealName(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return "N/A";
        }

        // 1. Check if the action was executed by an Employee
        org.bson.Document employeeDoc = mongoTemplate.findOne(
                new Query(Criteria.where("_id").is(userId)),
                org.bson.Document.class,
                "employees" // Make sure this matches your exact MongoDB employee collection name
        );
        if (employeeDoc != null && employeeDoc.containsKey("name")) {
            return employeeDoc.getString("name");
        }

        // 2. Fallback: Check if the action was executed by an Admin user
        org.bson.Document adminDoc = mongoTemplate.findOne(
                new Query(Criteria.where("_id").is(userId)),
                org.bson.Document.class,
                "admins" // Make sure this matches your exact MongoDB admin collection name
        );
        if (adminDoc != null && adminDoc.containsKey("name")) {
            return adminDoc.getString("name");
        }

        // 3. Fallback: Check Admin by user handling custom alternative identifier mappings if applicable
        Admin adminByHotel = mongoTemplate.findOne(
                new Query(Criteria.where("hotelId").is(userId)),
                Admin.class
        );
        if (adminByHotel != null) {
            return adminByHotel.getName();
        }

        // If no record is found anywhere, return the original ID safely instead of crashing
        return userId;
    }

    public void processExternalOrder(OrderWebhookDTO dto) {
        if (externalOrderRepository.existsByExternalOrderId(dto.getExternalOrderId())) {
            System.out.println("Duplicate order " + dto.getExternalOrderId() + " ignored.");
            return;
        }

        Admin admin = adminRepository.findByHotelId(dto.getHotelId())
                .orElseThrow(() -> new RuntimeException("Hotel not found"));

        String incomingMerchantId = dto.getMerchantId();
        String storedMerchantId = admin.getPlatformIds().get(dto.getPlatformName().toUpperCase());

        if (storedMerchantId == null || !storedMerchantId.equals(incomingMerchantId)) {
            throw new RuntimeException("Unauthorized: Merchant ID mismatch");
        }

        List<OrderItem> internalItems = dto.getItems().stream().map(extItem -> {
            OrderItem item = new OrderItem();
            item.setItemId("EXTERNAL");
            item.setItemName(extItem.getItemName());
            item.setQuantity(String.valueOf(extItem.getQuantity()));
            item.setPrice(extItem.getPrice());
            item.setSubTotal(extItem.getSubTotal());
            return item;
        }).collect(Collectors.toList());

        ExternalOrder externalOrder = ExternalOrder.builder()
                .hotelId(dto.getHotelId())
                .platform(dto.getPlatformName())
                .externalOrderId(dto.getExternalOrderId())
                .customerName(dto.getCustomerName())
                .customerMobile(dto.getCustomerContact())
                .deliveryAddress(dto.getDeliveryAddress())
                .items(internalItems)
                .totalAmount(dto.getTotalAmount())
                .status("RECEIVED")
                .receivedAt(LocalDateTime.now())
                .build();

        ExternalOrder savedOrder = externalOrderRepository.save(externalOrder);

        messagingTemplate.convertAndSend("/topic/orders/" + dto.getHotelId(), savedOrder);

        System.out.println("New " + dto.getPlatformName() + " order saved with ID: " + savedOrder.getId());
    }

    @Transactional
    public String approveExternalOrder(String orderIdIdentifier, String approvedBy) {
        ExternalOrder ext = externalOrderRepository.findById(orderIdIdentifier)
                .orElseGet(() -> externalOrderRepository.findByExternalOrderId(orderIdIdentifier)
                        .orElseThrow(() -> new RuntimeException("External order not found: " + orderIdIdentifier)));

        if ("ACCEPTED".equals(ext.getStatus())) {
            throw new RuntimeException("Order is already accepted");
        }

        ext.setStatus("ACCEPTED");
        externalOrderRepository.save(ext);

        KitchenOrder kitchenOrder = KitchenOrder.builder()
                .hotelId(ext.getHotelId())
                .tableName(null)
                .orderType("EXTERNAL_" + ext.getPlatform())
                .items(ext.getItems())
                .totalAmount(ext.getTotalAmount())
                .status("PENDING")
                .placedBy(approvedBy)
                .comments("DELIVERY: " + ext.getCustomerName() + " | " + ext.getCustomerMobile())
                .build();

        KitchenOrder saved = kitchenOrderRepository.save(kitchenOrder);

        versionService.bumpTables(ext.getHotelId());

        return saved.getId();
    }

    private void syncCustomerDetails(String hotelId, CompletedOrder bill) {
        String mobile = bill.getCustomerMobile();

        if (StringUtils.hasText(mobile) && !"0000000000".equals(mobile)) {

            Query query = new Query(Criteria.where("hotelId").is(hotelId)
                    .and("customerMobile").is(mobile));

            Update update = new Update()
                    .set("customerName", bill.getCustomerName())
                    .set("customerAddress", bill.getCustomerAddress())
                    .set("lastOrderDate", bill.getCheckoutDate())
                    .inc("totalOrders", 1)
                    .inc("totalAmountPaid", bill.getTotalPayable());

            mongoTemplate.upsert(query, update, CustomerDetails.class);
        }
    }

    private void updateTableVisualStatus(String hotelId, String tableName, String status) {
        if (tableName == null) return;

        tableRepository.findByHotelIdAndTableName(hotelId, tableName).ifPresent(t -> {
            t.setStatus(status);
            t.setUpdatedAt(LocalDateTime.now());
            tableRepository.save(t);
        });
    }

    private void updateTableBill(String hotelId, String tableName, Double amountToAdd, boolean isReset) {
        if (tableName == null) return;

        tableRepository.findByHotelIdAndTableName(hotelId, tableName).ifPresent(table -> {
            if (isReset) {
                table.setCurrentBill(0.0);
                table.setStatus("INACTIVE");
            } else {
                Double existingBill = table.getCurrentBill() != null ? table.getCurrentBill() : 0.0;
                double newTotal = Math.max(0.0, existingBill + (amountToAdd != null ? amountToAdd : 0.0));
                table.setCurrentBill(newTotal);

                if (newTotal <= 0) {
                    table.setStatus("INACTIVE");
                } else {
                    table.setStatus("PENDING");
                }
            }
            table.setUpdatedAt(LocalDateTime.now());
            tableRepository.save(table);
            log.info("BILL_SYNC: Hotel {} Table {} updated to {}. Status: {}",
                    hotelId, tableName, table.getCurrentBill(), table.getStatus());
        });
    }

    public SalesAnalyticsDTO getTodayHourlySales(String hotelId) {
        return salesAggregationRepository.getTodayAnalytics(hotelId);
    }

    public SalesAnalyticsDTO getCurrentWeekSales(String hotelId) {
        return salesAggregationRepository.getWeekAnalytics(hotelId);
    }

    public SalesAnalyticsDTO getCurrentMonthSales(String hotelId) {
        return salesAggregationRepository.getMonthAnalytics(hotelId);
    }

    public SalesAnalyticsDTO getCurrentYearSales(String hotelId) {
        return salesAggregationRepository.getYearAnalytics(hotelId);
    }

    private ZonedDateTime getISTNow() {
        return ZonedDateTime.now(IST);
    }
}
