package com.raghunath.hotelview.service.admin;

import com.raghunath.hotelview.dto.admin.*;
import com.raghunath.hotelview.entity.*;
import com.raghunath.hotelview.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

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
     * Since the waiter has full access, this updates the live table total immediately.
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
     * Table status becomes 'PENDING' to alert the Chef.
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
                .createdBy(waiterId)
                .createdAt(nowIST.toLocalDateTime())
                .createdDate(nowIST.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                .createdTime(nowIST.format(DateTimeFormatter.ofPattern("HH:mm:ss")))
                .build();

        kitchenOrderRepository.save(kOrder);
        draftRepository.findByHotelIdAndTableName(hotelId, tableName).ifPresent(draftRepository::delete);

        updateTableVisualStatus(hotelId, tableName, "PENDING");
        updateTableBill(hotelId, tableName, total, false);

        return "Order sent to kitchen";
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

        // 2. Original Logic Starts Here
        ZonedDateTime nowIST = getISTNow();
        Double total = items.stream().mapToDouble(OrderItem::getSubTotal).sum();

        KitchenOrder deliveryOrder = KitchenOrder.builder()
                .hotelId(hotelId)
                .tableName(null)
                .orderType(orderType.toUpperCase())
                .items(items)
                .totalAmount(total)
                .status("PENDING")
                .createdBy(waiterId)
                .createdAt(nowIST.toLocalDateTime())
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
        return kitchenOrderRepository.findByHotelIdAndTableNameAndStatusNotOrderByCreatedAtDesc(
                hotelId, tableName, "PAID");
    }

    /**
     * 6. CHECKOUT ORDERS: checkout all orders.
     */
    @Transactional
    public CheckoutResponse checkoutOrders(String hotelId, CheckoutRequest request) {
        // 1. Subscription Check (Existing)
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

        // 3. Aggregate Items (Unique Items and Totals)
        Map<String, CheckoutResponse.BillItem> itemMap = new HashMap<>();
        for (KitchenOrder order : activeOrders) {
            order.getItems().forEach(item -> {
                itemMap.merge(item.getItemName(),
                        CheckoutResponse.BillItem.builder()
                                .itemName(item.getItemName())
                                .quantity(item.getQuantity())
                                .price(item.getPrice() * item.getQuantity())
                                .build(),
                        (oldVal, newVal) -> {
                            oldVal.setQuantity(oldVal.getQuantity() + newVal.getQuantity());
                            oldVal.setPrice(oldVal.getPrice() + newVal.getPrice());
                            return oldVal;
                        });
            });
        }

        Double grandTotal = activeOrders.stream().mapToDouble(KitchenOrder::getTotalAmount).sum();
        Double discountPercent = (request.getDiscount() != null) ? request.getDiscount() : 0.0;
        Double totalPayable = grandTotal - ((grandTotal * discountPercent) / 100);

        ZonedDateTime nowIST = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));

        // 4. Build and Save Completed Order
        CompletedOrder finalBill = CompletedOrder.builder()
                .hotelId(hotelId)
                .orderType(activeOrders.get(0).getOrderType())
                .customerName(StringUtils.hasText(request.getCustomerName()) ? request.getCustomerName() : "Walk-in Guest")
                .customerMobile(StringUtils.hasText(request.getCustomerMobile()) ? request.getCustomerMobile() : "0000000000")
                .customerAddress(StringUtils.hasText(request.getCustomerAddress()) ? request.getCustomerAddress() : "N/A")
                .allOrders(activeOrders)
                .grandTotal(grandTotal)
                .totalPayable(totalPayable)
                .checkoutAt(nowIST.toLocalDateTime())
                .checkoutDate(nowIST.format(DateTimeFormatter.ISO_LOCAL_DATE))
                .checkoutTime(nowIST.format(DateTimeFormatter.ofPattern("HH:mm:ss")))
                .build();

        CompletedOrder savedBill = completeOrderRepository.save(finalBill);

        // 5. Cleanup and Sync
        if (savedBill.getId() != null) {
            syncCustomerDetails(hotelId, finalBill);
            kitchenOrderRepository.deleteAll(activeOrders);
            // ... (Table visual status logic same as before)
        }

        // 6. Build the Detailed Response
        String fullId = savedBill.getId();
        String shortId = (fullId != null && fullId.length() > 6) ? fullId.substring(fullId.length() - 6) : fullId;

        return CheckoutResponse.builder()
                .id(fullId)
                .shortId(shortId)
                .checkoutDate(finalBill.getCheckoutDate())
                .checkoutTime(finalBill.getCheckoutTime())
                .orderType(finalBill.getOrderType())
                .customerName(finalBill.getCustomerName())
                .customerMobile(finalBill.getCustomerMobile())
                .customerAddress(finalBill.getCustomerAddress())
                .items(new ArrayList<>(itemMap.values()))
                .grandTotal(grandTotal)
                .totalPayable(totalPayable)
                .build();
    }

    /**
     * 7. FETCH DASHBOARD STATS: Consolidates metrics from 5 different collections.
     */
    public DashboardStatsDTO getDashboardStats(String hotelId) {
        // 1. Time Setup (Indian Standard Time)
        ZonedDateTime nowIST = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
        String todayDate = nowIST.format(DateTimeFormatter.ISO_LOCAL_DATE);

        // 2. Fetch Admin Details (Name & Plan Type)
        Admin admin = adminRepository.findByHotelId(hotelId)
                .orElseThrow(() -> new RuntimeException("Hotel Admin not found"));

        String restaurantName = admin.getRestaurantName() != null ? admin.getRestaurantName() : "Unknown Restaurant";
        String planType = admin.getPlanType() != null ? admin.getPlanType() : "BASIC"; // Default to BASIC if null

        // 3. Fetch Metrics
        List<String> activeStatuses = List.of("PENDING", "ACCEPTED", "ACTIVE");
        Long activeTablesCount = tableRepository.countByHotelIdAndStatusIn(hotelId, activeStatuses);

        List<String> deliveryTypes = List.of("HOME", "PARCEL");
        Long homeAndParcelOrdersToday = completeOrderRepository.countByHotelIdAndOrderTypeInAndCheckoutDate(
                hotelId, deliveryTypes, todayDate);

        Long employeeCount = employeeRepository.countByHotelIdAndIsActive(hotelId, true);
        Long totalItems = menuItemRepository.countByHotelId(hotelId);
        Long completedTodayCount = completeOrderRepository.countByHotelIdAndCheckoutDate(
                hotelId, todayDate);

        // 4. Financial Aggregation
        Double todaySalesRupees = 0.0;
        try {
            Double result = completeOrderRepository.sumTotalPayableByHotelIdAndCheckoutDate(hotelId, todayDate);
            todaySalesRupees = (result != null) ? result : 0.0;
        } catch (Exception e) {
            log.error("AGGREGATION_ERROR: Sales sum failed for hotel {}", hotelId);
        }

        // 5. Build and return
        return DashboardStatsDTO.builder()
                .activeTablesCount(activeTablesCount)
                .HomeDeliveriesCount(homeAndParcelOrdersToday)
                .completedOrdersTodayCount(completedTodayCount)
                .employeeOnlineCount(employeeCount)
                .totalItemsCount(totalItems)
                .restaurantName(restaurantName)
                .planType(planType)
                .todaySalesRupees(todaySalesRupees)
                .build();
    }

    /**
     * 8. EDIT ORDER: edit order and save edit details.
     */
    @Transactional
    public void confirmOrderEdit(String hotelId, String orderId, List<OrderItem> newItems) {
        // 1. Fetch live order
        KitchenOrder order = kitchenOrderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        String userName = SecurityContextHolder.getContext().getAuthentication().getName();
        List<OrderEdit> editLogs = new ArrayList<>();
        String timeIST = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        // 🚀 STEP 2: Handle Reductions & Deletions (Match by Name)
        for (OrderItem oldItem : order.getItems()) {
            // Find matching item in the new list by Name
            OrderItem newItem = newItems.stream()
                    .filter(ni -> ni.getItemName().equalsIgnoreCase(oldItem.getItemName()))
                    .findFirst()
                    .orElse(null);

            int newQty = (newItem != null) ? newItem.getQuantity() : 0;
            int delta = newQty - oldItem.getQuantity();

            if (delta != 0) {
                editLogs.add(OrderEdit.builder()
                        .orderId(orderId)
                        .hotelId(hotelId)
                        .editedBy(userName)
                        .itemName(oldItem.getItemName())
                        .previousQty(oldItem.getQuantity())
                        .newQty(newQty)
                        .delta(delta)
                        .timestamp(timeIST)
                        .build());
            }
        }

        // 🚀 STEP 3: Handle Brand New Items (Items not in the old list)
        for (OrderItem newItem : newItems) {
            boolean exists = order.getItems().stream()
                    .anyMatch(old -> old.getItemName().equalsIgnoreCase(newItem.getItemName()));

            if (!exists) {
                editLogs.add(OrderEdit.builder()
                        .orderId(orderId)
                        .hotelId(hotelId)
                        .editedBy(userName)
                        .itemName(newItem.getItemName())
                        .previousQty(0)
                        .newQty(newItem.getQuantity())
                        .delta(newItem.getQuantity())
                        .timestamp(timeIST)
                        .build());
            }
        }

        // 4. Update the Database
        double oldTotal = order.getTotalAmount();
        double newTotal = newItems.stream().mapToDouble(i -> i.getPrice() * i.getQuantity()).sum();

        order.setItems(newItems);
        order.setTotalAmount(newTotal);
        kitchenOrderRepository.save(order);

        // 5. Update Table Bill Difference
        updateTableBill(hotelId, order.getTableName(), (newTotal - oldTotal), false);

        // 6. Save Audit Logs to the separate collection
        if (!editLogs.isEmpty()) {
            mongoTemplate.insert(editLogs, "order_edits");
        }

        versionService.bumpTables(hotelId);
    }

    /**
     * 9. Get Full Table History via Completed Order ID
     */
    public Map<String, Object> getAggregatedTableSummary(String hotelId, String completedOrderId) {
        // 1. Fetch the main Completed Order document (The "Parent")
        CompletedOrder completedBill = mongoTemplate.findById(completedOrderId, CompletedOrder.class);

        if (completedBill == null) {
            throw new RuntimeException("Completed Order record not found");
        }

        // 2. Prepare a LinkedHashMap to maintain the order of arrival (Serial View)
        Map<String, Object> finalTableHistory = new LinkedHashMap<>();

        // 3. Loop through every KitchenOrder that was part of this bill
        for (KitchenOrder subOrder : completedBill.getAllOrders()) {
            String subOrderId = subOrder.getId();

            // Query audit logs for THIS specific sub-order
            Query query = new Query(Criteria.where("orderId").is(subOrderId).and("hotelId").is(hotelId));
            query.with(Sort.by(Sort.Direction.ASC, "timestamp"));

            List<OrderEdit> editHistory = mongoTemplate.find(query, OrderEdit.class, "order_edits");

            // Prepare the data packet for this specific order
            Map<String, Object> orderEntry = new LinkedHashMap<>();
            orderEntry.put("orderType", subOrder.getOrderType());
            orderEntry.put("finalItems", subOrder.getItems()); // Items as they appeared at checkout
            orderEntry.put("totalAmount", subOrder.getTotalAmount());

            // Logical check for "No Edits"
            if (editHistory.isEmpty()) {
                orderEntry.put("editSummary", "No order edit summary available");
            } else {
                orderEntry.put("editSummary", editHistory);
            }

            // Add to the main map using the Order ID as the key
            finalTableHistory.put("Order_" + subOrderId, orderEntry);
        }

        return finalTableHistory;
    }

    // Inside OrderService.java

    public void processExternalOrder(OrderWebhookDTO dto) {
        // 1. DEDUPLICATION: Prevent double-processing if Zomato retries the request
        if (externalOrderRepository.existsByExternalOrderId(dto.getExternalOrderId())) {
            System.out.println("Duplicate order " + dto.getExternalOrderId() + " ignored.");
            return;
        }

        // 2. AUTHENTICATION: Verify Hotel and Merchant ID
        Admin admin = adminRepository.findByHotelId(dto.getHotelId())
                .orElseThrow(() -> new RuntimeException("Hotel not found"));

        String incomingMerchantId = dto.getMerchantId();
        String storedMerchantId = admin.getPlatformIds().get(dto.getPlatformName().toUpperCase());

        if (storedMerchantId == null || !storedMerchantId.equals(incomingMerchantId)) {
            throw new RuntimeException("Unauthorized: Merchant ID mismatch");
        }

        // 3. MAPPING: External items to Internal items
        List<OrderItem> internalItems = dto.getItems().stream().map(extItem -> {
            OrderItem item = new OrderItem();
            item.setItemId("EXTERNAL");
            item.setItemName(extItem.getItemName());
            item.setQuantity(extItem.getQuantity());
            item.setPrice(extItem.getPrice());
            item.setSubTotal(extItem.getSubTotal());
            return item;
        }).collect(Collectors.toList());

        // 4. PERSISTENCE: Save to Database FIRST
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

        // The order is saved and the transaction is committed here
        ExternalOrder savedOrder = externalOrderRepository.save(externalOrder);

        // 5. BROADCAST: Push via WebSocket only AFTER successful save
        // This prevents the Race Condition Krishna is seeing
        messagingTemplate.convertAndSend("/topic/orders/" + dto.getHotelId(), savedOrder);

        System.out.println("New " + dto.getPlatformName() + " order saved with ID: " + savedOrder.getId());
    }

    // NEW: Approve External Order (Moves it to Kitchen)
    @Transactional
    public String approveExternalOrder(String orderIdIdentifier, String approvedBy) {
        // 1. DUAL-ID LOOKUP: Try MongoDB ID first, then fallback to External Order ID
        // This ensures that even if Krishna sends the wrong ID format, it still works.
        ExternalOrder ext = externalOrderRepository.findById(orderIdIdentifier)
                .orElseGet(() -> externalOrderRepository.findByExternalOrderId(orderIdIdentifier)
                        .orElseThrow(() -> new RuntimeException("External order not found: " + orderIdIdentifier)));

        // 2. STATE GUARD: Prevent double-acceptance
        if ("ACCEPTED".equals(ext.getStatus())) {
            throw new RuntimeException("Order is already accepted");
        }

        // 3. UPDATE EXTERNAL STATUS
        ext.setStatus("ACCEPTED");
        externalOrderRepository.save(ext);

        // 4. KITCHEN TRANSFORMATION
        KitchenOrder kitchenOrder = KitchenOrder.builder()
                .hotelId(ext.getHotelId())
                .tableName(null) // NULL signals Home Delivery
                .orderType("EXTERNAL_" + ext.getPlatform())
                .items(ext.getItems())
                .totalAmount(ext.getTotalAmount())
                .status("PENDING")
                .createdBy(approvedBy)
                .createdAt(LocalDateTime.now())
                .comments("DELIVERY: " + ext.getCustomerName() + " | " + ext.getCustomerMobile())
                .build();

        KitchenOrder saved = kitchenOrderRepository.save(kitchenOrder);

        // 5. VERSION BUMP: Notifies the Chef's UI to refresh
        versionService.bumpTables(ext.getHotelId());

        return saved.getId();
    }

    /**
     * Helper method to handle Upsert logic for customer details
     */
    private void syncCustomerDetails(String hotelId, CompletedOrder bill) {
        String mobile = bill.getCustomerMobile();

        // Condition: Only proceed if number is provided and is not the dummy "0000000000"
        if (StringUtils.hasText(mobile) && !"0000000000".equals(mobile)) {

            Query query = new Query(Criteria.where("hotelId").is(hotelId)
                    .and("customerMobile").is(mobile));

            Update update = new Update()
                    .set("customerName", bill.getCustomerName())
                    .set("customerAddress", bill.getCustomerAddress())
                    .set("lastOrderDate", bill.getCheckoutAt())
                    .inc("totalOrders", 1)               // Increment order count by 1
                    .inc("totalAmountPaid", bill.getTotalPayable()); // Add payable to total

            // upsert: true means if not found, create new; if found, update existing
            mongoTemplate.upsert(query, update, CustomerDetails.class);
        }
    }

    /**
     * HELPER: Syncs the physical Table entity with the digital order status.
     */
    private void updateTableVisualStatus(String hotelId, String tableName, String status) {
        if (tableName == null) return; // Never update table UI for delivery

        tableRepository.findByHotelIdAndTableName(hotelId, tableName).ifPresent(t -> {
            t.setStatus(status);
            t.setUpdatedAt(LocalDateTime.now());
            tableRepository.save(t);
        });
    }

    // Inside OrderService.java (Add the new method)

    private void updateTableBill(String hotelId, String tableName, Double amountToAdd, boolean isReset) {
        if (tableName == null) return;

        tableRepository.findByHotelIdAndTableName(hotelId, tableName).ifPresent(table -> {
            if (isReset) {
                table.setCurrentBill(0.0);
                table.setStatus("INACTIVE"); //  Your Inactive Status
            } else {
                Double existingBill = table.getCurrentBill() != null ? table.getCurrentBill() : 0.0;
                double newTotal = Math.max(0.0, existingBill + (amountToAdd != null ? amountToAdd : 0.0));
                table.setCurrentBill(newTotal);

                // Logic for your 3 specific statuses
                if (newTotal <= 0) {
                    table.setStatus("INACTIVE");
                } else {
                    table.setStatus("PENDING"); //  Bill is updated, waiting for next action/payment
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