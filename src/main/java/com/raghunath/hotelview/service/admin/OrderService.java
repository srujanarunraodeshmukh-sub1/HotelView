package com.raghunath.hotelview.service.admin;

import com.raghunath.hotelview.dto.admin.*;
import com.raghunath.hotelview.entity.Admin;
import com.raghunath.hotelview.entity.CompletedOrder;
import com.raghunath.hotelview.entity.KitchenOrder;
import com.raghunath.hotelview.entity.OrderDraft;
import com.raghunath.hotelview.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

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
    private final SalesAggregationRepository salesAggregationRepository;
    private final VersionService versionService;

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    /**
     * 1. SAVE DRAFT: Waiter/Admin adds items.
     * Since the waiter has full access, this updates the live table total immediately.
     */
    public void saveDraft(String hotelId, int tableNumber, List<OrderItem> items) {
        validateTableExists(hotelId, tableNumber);
        Double total = items.stream().mapToDouble(OrderItem::getSubTotal).sum();

        OrderDraft draft = draftRepository.findByHotelIdAndTableNumber(hotelId, tableNumber)
                .orElse(OrderDraft.builder()
                        .hotelId(hotelId)
                        .tableNumber(tableNumber)
                        .build());

        draft.setItems(items);
        draft.setTotalAmount(total);
        draft.setUpdatedAt(LocalDateTime.now());
        draftRepository.save(draft);
    }


    private void validateTableExists(String hotelId, int tableNumber) {
        if (!tableRepository.existsByHotelIdAndTableNumber(hotelId, tableNumber)) {
            log.error("VALIDATION_FAILED: Table {} not found for Hotel {}", tableNumber, hotelId);
            throw new RuntimeException("Invalid Table Number: " + tableNumber);
        }
    }

    /**
     * 2. FETCH DRAFT: View current unsent items.
     */
    public OrderDraft getDraft(String hotelId, int tableNumber) {
        return draftRepository.findByHotelIdAndTableNumber(hotelId, tableNumber).orElse(null);
    }

    /**
     * 3. CONFIRM ORDER: Moves items to the Kitchen.
     * Table status becomes 'PENDING' to alert the Chef.
     */
    @Transactional
    public String confirmOrder(String hotelId, int tableNumber, List<OrderItem> items, String waiterId, String comment) {
        validateTableExists(hotelId, tableNumber);
        ZonedDateTime nowIST = getISTNow();
        Double total = items.stream().mapToDouble(OrderItem::getSubTotal).sum();

        KitchenOrder kOrder = KitchenOrder.builder()
                .hotelId(hotelId)
                .tableNumber(tableNumber)
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
        draftRepository.findByHotelIdAndTableNumber(hotelId, tableNumber).ifPresent(draftRepository::delete);

        // --- ADD THESE TWO LINES HERE ---
        updateTableVisualStatus(hotelId, tableNumber, "PENDING");
        updateTableBill(hotelId, tableNumber, total, false); // Adds 'total' to the table's current bill

        return "Order sent to kitchen";
    }

    @Transactional
    public String confirmHomeDelivery(String hotelId, List<OrderItem> items, String waiterId, String orderType) {
        ZonedDateTime nowIST = getISTNow();
        Double total = items.stream().mapToDouble(OrderItem::getSubTotal).sum();

        KitchenOrder deliveryOrder = KitchenOrder.builder()
                .hotelId(hotelId)
                .tableNumber(null)
                .orderType(orderType.toUpperCase()) // Save the user-provided type
                .items(items)
                .totalAmount(total)
                .status("PENDING")
                .createdBy(waiterId)
                .createdAt(nowIST.toLocalDateTime())
                .createdDate(nowIST.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                .createdTime(nowIST.format(DateTimeFormatter.ofPattern("HH:mm:ss")))
                .build();

        String savedId = kitchenOrderRepository.save(deliveryOrder).getId();

        // 🚀 SYNC TRIGGER: Tell the Chef and Admin a new order arrived
        // Even though there's no table, the Chef's "Pending Orders" version needs to bump
        versionService.bumpTables(hotelId);

        return savedId;
    }
    /**
     * 4. FETCH TABLE ORDERS: Latest orders first.
     */
    public List<KitchenOrder> getOrdersByTable(String hotelId, int tableNumber) {
        return kitchenOrderRepository.findByHotelIdAndTableNumberAndStatusNotOrderByCreatedAtDesc(
                hotelId, tableNumber, "PAID");
    }

    /**
     * 5. UPDATE STATUS WITH CHEF: Handles Kitchen Lifecycle.
     * PREPARING -> Table shows 'PREPARING'
     * COMPLETED -> Table shows 'ACTIVE' (Food is served/Guest eating)
     */
    public void updateStatusWithChef(String orderId, String newStatus, String userId) {
        KitchenOrder order = kitchenOrderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        // ✅ Set updatedAt directly on entity and save
        order.setStatus(newStatus.toUpperCase());
        order.setUpdatedAt(LocalDateTime.now());

        if ("PREPARING".equalsIgnoreCase(newStatus)) {
            order.setAcceptedBy(userId);
        }

        kitchenOrderRepository.save(order);

        // Bump kitchen version so Krishna knows something changed
        versionService.bumpKitchen(order.getHotelId());

        // Syncing Table Status
        if ("TABLE".equalsIgnoreCase(order.getOrderType())
                && order.getTableNumber() != null) {

            String tableUIStatus = switch (newStatus.toUpperCase()) {
                case "ACCEPTED", "PREPARING" -> "ACCEPTED";
                case "COMPLETED" -> "ACTIVE";
                default -> newStatus.toUpperCase();
            };

            updateTableVisualStatus(order.getHotelId(),
                    order.getTableNumber(), tableUIStatus);
        }
    }


    /**
     * 6. GENERAL STATUS UPDATE: Fallback for direct status changes.
     */
    public void updateOrderStatus(String orderId, String newStatus) {
        updateStatusWithChef(orderId, newStatus, null);
    }

    @Transactional
    public String checkoutOrders(String hotelId, CheckoutRequest request) {
        // 1. Fetch active orders
        List<KitchenOrder> activeOrders = kitchenOrderRepository.findAllById(request.getOrderIds());
        if (activeOrders.isEmpty()) {
            throw new RuntimeException("No active orders found");
        }

        // 2. Calculation Logic
        Double grandTotal = activeOrders.stream().mapToDouble(KitchenOrder::getTotalAmount).sum();

        // Get discount from request (default to 0 if null)
        Double discountPercent = (request.getDiscount() != null) ? request.getDiscount() : 0.0;
        Double discountAmount = (grandTotal * discountPercent) / 100;
        Double totalPayable = grandTotal - discountAmount;

        ZonedDateTime nowIST = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));

        // 3. Build the Archive Document
        CompletedOrder finalBill = CompletedOrder.builder()
                .hotelId(hotelId)
                .orderType(activeOrders.get(0).getOrderType())
                .customerName(StringUtils.hasText(request.getCustomerName()) ? request.getCustomerName() : "Walk-in Guest")
                .customerMobile(StringUtils.hasText(request.getCustomerMobile()) ? request.getCustomerMobile() : "0000000000")
                .customerAddress(StringUtils.hasText(request.getCustomerAddress()) ? request.getCustomerAddress() : "N/A")

                // New Fields for Pricing
                .allOrders(activeOrders)
                .grandTotal(grandTotal)
                .discountPercent(discountPercent)
                .discountAmount(discountAmount)
                .totalPayable(totalPayable)

                .paymentStatus("PAID")
                .checkoutAt(nowIST.toLocalDateTime())
                .checkoutDate(nowIST.format(DateTimeFormatter.ISO_LOCAL_DATE))
                .checkoutTime(nowIST.format(DateTimeFormatter.ofPattern("HH:mm:ss")))
                .lastModified(LocalDateTime.now())
                .build();

        // 4. Save and Cleanup
        CompletedOrder savedBill = completeOrderRepository.save(finalBill);
        if (savedBill.getId() != null) {
            kitchenOrderRepository.deleteAll(activeOrders);

            KitchenOrder first = activeOrders.get(0);
            if ("TABLE".equalsIgnoreCase(first.getOrderType()) && first.getTableNumber() != null) {
                updateTableVisualStatus(hotelId, first.getTableNumber(), "INACTIVE");
                updateTableBill(hotelId, first.getTableNumber(), 0.0, true);

                // 🚀 Tell Waiters: Table is free
                versionService.bumpTables(hotelId);
            }

            // 🚀 Tell Admins: New Sales Data available
            versionService.bumpSales(hotelId);
        }
        return savedBill.getId();
    }
    // --- API 1: Paged Fetch (5 at a time) ---
    public Page<DeliverySummaryDTO> getCompletedOrdersPaged(String hotelId, int pageNumber) {
        Pageable pageable = PageRequest.of(pageNumber, 10, Sort.by("checkoutAt").descending());

        // 1. Fetch the Entity Page
        Page<CompletedOrder> entities = completeOrderRepository.findByHotelId(hotelId, pageable);

        // 2. Map Entities to DeliverySummaryDTO
        return entities.map(order -> DeliverySummaryDTO.builder()
                .id(order.getId())
                .orderType(order.getOrderType())
                .customerName(order.getCustomerName())
                .customerMobile(order.getCustomerMobile())
                .totalPayable(order.getTotalPayable()) // 👈 Map the correct field here
                .checkoutAt(order.getCheckoutAt())
                .build());
    }

    // --- API 2: Full Detail by ID ---
    public CompletedOrder getCompletedOrderDetails(String id) {
        return completeOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order history not found for ID: " + id));
    }

    // --- API 3: Search by Name or Mobile ---
    public List<CompletedOrder> searchCompletedOrders(String hotelId, String query) {
        return completeOrderRepository.searchOrders(hotelId, query);
    }

    public ReceiptResponse getReceiptDetails(String orderId, String hotelIdFromToken) {
        // 1. Fetch the Completed Order
        CompletedOrder order = completeOrderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        // Security Check: Ensure this order belongs to the hotel in the token
        if (!order.getHotelId().equals(hotelIdFromToken)) {
            throw new RuntimeException("Unauthorized access to this order");
        }

        // 2. Fetch Restaurant Details from Admin Entity
        Admin admin = (Admin) adminRepository.findByHotelId(hotelIdFromToken)
                .orElseThrow(() -> new RuntimeException("Restaurant profile not found"));

        // 3. Flatten all nested items from allOrders array into one list for the receipt
        List<ReceiptResponse.FlattenedItem> flattenedItems = order.getAllOrders().stream()
                .flatMap(kitchenOrder -> kitchenOrder.getItems().stream())
                .map(item -> ReceiptResponse.FlattenedItem.builder()
                        .itemName(item.getItemName())
                        .quantity(item.getQuantity())
                        .price(item.getPrice())
                        .subTotal(item.getSubTotal())
                        .build())
                .collect(Collectors.toList());

        // 4. Build the final Print-Ready response
        return ReceiptResponse.builder()
                .restaurantName(admin.getRestaurantName())
                .restaurantAddress(admin.getRestaurantAddress())
                .restaurantContact(admin.getRestaurantContact())
                .restaurantLogo(admin.getRestaurantLogo())
                .restaurantUpi(admin.getRestaurantUpi())
                .orderId(order.getId())
                .date(order.getCheckoutDate())
                .time(order.getCheckoutTime())
                .orderType(order.getOrderType())
                .items(flattenedItems)

                // --- NEW PRICING MAPPING ---
                .grandTotal(order.getGrandTotal())
                .discountPercent(order.getDiscountPercent())
                .discountAmount(order.getDiscountAmount())
                .totalPayable(order.getTotalPayable())
                // ---------------------------

                .customerName(order.getCustomerName())
                .customerMobile(order.getCustomerMobile())
                .customerAddress(order.getCustomerAddress())
                .build();
    }
    /**
     * HELPER: Syncs the physical Table entity with the digital order status.
     */
    private void updateTableVisualStatus(String hotelId, Integer tableNumber, String status) {
        if (tableNumber == null) return; // Never update table UI for delivery

        tableRepository.findByHotelIdAndTableNumber(hotelId, tableNumber).ifPresent(t -> {
            t.setStatus(status);
            t.setUpdatedAt(LocalDateTime.now());
            tableRepository.save(t);
        });
    }

    @Transactional
    public void softDeleteOrders(String hotelId, List<String> orderIds) {
        // 1. Fetch orders from both sources
        List<CompletedOrder> completedOrders = completeOrderRepository.findAllById(orderIds)
                .stream().filter(o -> o.getHotelId().equals(hotelId)).toList();

        List<KitchenOrder> kitchenOrders = kitchenOrderRepository.findAllById(orderIds)
                .stream().filter(o -> o.getHotelId().equals(hotelId)).toList();

        if (completedOrders.isEmpty() && kitchenOrders.isEmpty()) {
            throw new RuntimeException("Unauthorized or Orders not found");
        }

        // 🚀 2. Calculate deduction and identify tables involved
        double totalDeduction = 0.0;
        Set<Integer> tablesToUpdate = new HashSet<>();

        // FIX: Get tableNumber from the nested list in CompletedOrder
        for (CompletedOrder o : completedOrders) {
            totalDeduction += (o.getTotalPayable() != null) ? o.getTotalPayable() : 0.0;

            if (o.getAllOrders() != null && !o.getAllOrders().isEmpty()) {
                Integer tNum = o.getAllOrders().get(0).getTableNumber();
                if (tNum != null) tablesToUpdate.add(tNum);
            }
        }

        // Process Kitchen Orders normally
        for (KitchenOrder o : kitchenOrders) {
            totalDeduction += (o.getTotalAmount() != null) ? o.getTotalAmount() : 0.0;
            if (o.getTableNumber() != null) {
                tablesToUpdate.add(o.getTableNumber());
            }
        }

        // 3. Update the RestaurantTable currentBill
        if (!tablesToUpdate.isEmpty()) {
            for (Integer tableNum : tablesToUpdate) {
                double finalTotalDeduction = totalDeduction;
                tableRepository.findByHotelIdAndTableNumber(hotelId, tableNum).ifPresent(table -> {
                    double current = (table.getCurrentBill() != null) ? table.getCurrentBill() : 0.0;
                    double newBill = Math.max(0, current - finalTotalDeduction);

                    table.setCurrentBill(newBill);

                    // If bill becomes 0, set status to AVAILABLE
                    if (newBill <= 0) {
                        table.setStatus("AVAILABLE");
                    }

                    tableRepository.save(table);
                });
            }
        }

        // 4. Move to Trash with deletedAt IST
        ZonedDateTime nowIST = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
        String deletedAt = nowIST.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        List<org.bson.Document> trashDocs = new ArrayList<>();

        // Convert and add deletedAt to documents
        completedOrders.forEach(order -> {
            org.bson.Document doc = new org.bson.Document();
            mongoTemplate.getConverter().write(order, doc);
            doc.put("deletedAt", deletedAt);
            trashDocs.add(doc);
        });

        kitchenOrders.forEach(order -> {
            org.bson.Document doc = new org.bson.Document();
            mongoTemplate.getConverter().write(order, doc);
            doc.put("deletedAt", deletedAt);
            trashDocs.add(doc);
        });

        // 5. Final DB Operations
        if (!trashDocs.isEmpty()) {
            mongoTemplate.insert(trashDocs, "deleted_orders");
            if (!completedOrders.isEmpty()) completeOrderRepository.deleteAll(completedOrders);
            if (!kitchenOrders.isEmpty()) kitchenOrderRepository.deleteAll(kitchenOrders);
        }

        // 6. Sync Versions
        versionService.bumpSales(hotelId);
        versionService.bumpTables(hotelId);
    }

    public List<org.bson.Document> getDeletedOrders(String hotelId) {
        // 1. Query for the specific hotel
        Query query = new Query(Criteria.where("hotelId").is(hotelId));

        // 2. Fetch as raw Documents from the 'deleted_orders' collection
        // This bypasses Java class restrictions and gets EVERY field (items, amount, etc.)
        List<org.bson.Document> rawDocs = mongoTemplate.find(query, org.bson.Document.class, "deleted_orders");

        // 3. Clean up the response for the frontend
        return rawDocs.stream().map(doc -> {
            // Convert ObjectId to String for the 'id' field
            if (doc.containsKey("_id")) {
                doc.put("id", doc.get("_id").toString());
                doc.remove("_id");
            }
            // Remove the internal Java class metadata so the JSON is clean
            doc.remove("_class");
            return doc;
        }).collect(java.util.stream.Collectors.toList());
    }

    /**
     * FETCH TODAY'S COMPLETED HOME DELIVERIES (Clean Summary):
     * Returns only the essential fields for the dashboard list.
     */
    public List<DeliverySummaryDTO> getTodayCompletedHomeDeliveries(String hotelId) {
        ZonedDateTime nowIST = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
        String todayDate = nowIST.format(DateTimeFormatter.ISO_LOCAL_DATE);

        // 🚀 Update: Define the list of types
        List<String> externalTypes = List.of("HOME", "PARCEL");

        // 🚀 Update: Use the In query with the list
        List<CompletedOrder> orders = completeOrderRepository
                .findByHotelIdAndOrderTypeInAndCheckoutDateOrderByCheckoutAtDesc(
                        hotelId, externalTypes, todayDate);

        // Map Entity to DTO (Kept exactly as you provided)
        return orders.stream().map(order -> DeliverySummaryDTO.builder()
                        .id(order.getId())
                        .orderType(order.getOrderType())
                        .customerName(order.getCustomerName())
                        .customerMobile(order.getCustomerMobile())
                        .totalPayable(order.getTotalPayable())
                        .checkoutAt(order.getCheckoutAt())
                        .build())
                .collect(Collectors.toList());
    }

    // Inside OrderService.java (Add the new method)

    /**
     * FETCH DASHBOARD STATS: Consolidates metrics from 5 different collections.
     */
    public DashboardStatsDTO getDashboardStats(String hotelId) {
        // 1. Time Setup (Indian Standard Time)
        ZonedDateTime nowIST = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
        String todayDate = nowIST.format(DateTimeFormatter.ISO_LOCAL_DATE); // yyyy-MM-dd

        // 2. Fetch Metrics

        // FIX 1: Active Tables (Sum of PENDING, ACCEPTED, and ACTIVE statuses)
        // Production Note: Using a List of statuses is more accurate than just "NOT INACTIVE"
        List<String> activeStatuses = List.of("PENDING", "ACCEPTED", "ACTIVE");
        Long activeTablesCount = tableRepository.countByHotelIdAndStatusIn(hotelId, activeStatuses);

        // FIX 2: Home Delivery Count (Pulling from COMPLETED orders for today)
        Long homeDeliveriesToday = completeOrderRepository.countByHotelIdAndOrderTypeAndCheckoutDate(
                hotelId, "HOME_DELIVERY", todayDate);

        // Employee Stats: Count all active employees
        Long employeeCount = employeeRepository.countByHotelIdAndIsActive(hotelId, true);

        // Menu Item Stats: Count all items for this hotel
        Long totalItems = menuItemRepository.countByHotelId(hotelId);

        // Completed Orders Today count (Total Bills issued today)
        Long completedTodayCount = completeOrderRepository.countByHotelIdAndCheckoutDate(
                hotelId, todayDate);

        // 3. Financial Aggregation (Actual Revenue after discount)
        Double todaySalesRupees = 0.0;
        try {
            Double result = completeOrderRepository.sumTotalPayableByHotelIdAndCheckoutDate(hotelId, todayDate);
            todaySalesRupees = (result != null) ? result : 0.0;
        } catch (Exception e) {
            log.error("AGGREGATION_ERROR: Sales sum failed for hotel {}", hotelId);
            todaySalesRupees = 0.0;
        }

        // 4. Build and return the consolidated DTO
        return DashboardStatsDTO.builder()
                .activeTablesCount(activeTablesCount) // Now shows sum of Active/Pending/Accepted
                .pendingHomeDeliveriesCount(homeDeliveriesToday) // Now shows Total Completed Deliveries Today
                .completedOrdersTodayCount(completedTodayCount)
                .employeeOnlineCount(employeeCount)
                .totalItemsCount(totalItems)
                .todaySalesRupees(todaySalesRupees)
                .build();
    }

    private void updateTableBill(String hotelId, Integer tableNumber, Double amountToAdd, boolean isReset) {
        if (tableNumber == null) return;

        tableRepository.findByHotelIdAndTableNumber(hotelId, tableNumber).ifPresent(table -> {
            if (isReset) {
                table.setCurrentBill(0.0);
            } else {
                // Logic: Existing Bill + New Order Total
                Double existingBill = table.getCurrentBill() != null ? table.getCurrentBill() : 0.0;
                table.setCurrentBill(existingBill + amountToAdd);
            }
            table.setUpdatedAt(LocalDateTime.now());
            tableRepository.save(table);
            log.info("BILL_SYNC: Hotel {} Table {} updated to {}", hotelId, tableNumber, table.getCurrentBill());
        });
    }

    private ZonedDateTime getISTNow() {
        return ZonedDateTime.now(IST);
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
}