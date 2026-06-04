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
                .totalPayable(totalPayable)
                .build();
    }

    /**
     * 7. INSTANT CHECKOUT
     */
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

    /**
     * 8. FETCH DASHBOARD STATS
     */
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
                .completedOrdersTodayCount(completedTodayCount)
                .employeeOnlineCount(employeeCount)
                .totalItemsCount(totalItems)
                .restaurantName(restaurantName)
                .planType(planType)
                .todaySalesRupees(todaySalesRupees)
                .last24HoursSalesRupees(last24HoursSales)
                .last24HoursOrdersCount(last24HoursOrders)
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
    public void confirmOrderEdit(String hotelId, String orderId, List<OrderItem> newItems) {
        KitchenOrder order = kitchenOrderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        String userName = SecurityContextHolder.getContext().getAuthentication().getName();
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
                        .editedBy(userName)
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
                        .editedBy(userName)
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

            if (editHistory.isEmpty()) {
                orderEntry.put("editSummary", "No order edit summary available");
            } else {
                orderEntry.put("editSummary", editHistory);
            }

            finalTableHistory.put("Order_" + subOrderId, orderEntry);
        }

        return finalTableHistory;
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
