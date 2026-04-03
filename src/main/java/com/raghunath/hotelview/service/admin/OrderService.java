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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.LocalDateTime;
import java.util.List;
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

    private ZonedDateTime getISTNow() {
        return ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
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
    public String confirmOrder(String hotelId, int tableNumber, List<OrderItem> items, String waiterId) {
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

        return "Table order sent to kitchen";
    }

    @Transactional
    public String confirmHomeDelivery(String hotelId, List<OrderItem> items, String waiterId) {
        ZonedDateTime nowIST = getISTNow();
        Double total = items.stream().mapToDouble(OrderItem::getSubTotal).sum();

        KitchenOrder deliveryOrder = KitchenOrder.builder()
                .hotelId(hotelId)
                .tableNumber(null) // Keep null for delivery
                .orderType("HOME_DELIVERY")
                .items(items)
                .totalAmount(total)
                .status("PENDING")
                .createdBy(waiterId)
                .createdAt(nowIST.toLocalDateTime())
                .createdDate(nowIST.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                .createdTime(nowIST.format(DateTimeFormatter.ofPattern("HH:mm:ss")))
                .build();

        return kitchenOrderRepository.save(deliveryOrder).getId();
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

        Query query = new Query(Criteria.where("id").is(orderId));
        Update update = new Update();
        update.set("status", newStatus.toUpperCase());

        if ("PREPARING".equalsIgnoreCase(newStatus)) {
            update.set("acceptedBy", userId);
        }

        mongoTemplate.updateFirst(query, update, KitchenOrder.class);

        // Syncing Table Status
        if ("TABLE".equalsIgnoreCase(order.getOrderType()) && order.getTableNumber() != null) {
            String tableUIStatus;

            switch (newStatus.toUpperCase()) {
                case "ACCEPTED":
                case "PREPARING":
                    tableUIStatus = "ACCEPTED";
                    break;
                case "COMPLETED":
                    tableUIStatus = "ACTIVE"; // Chef finished, customer is eating
                    break;
                default:
                    tableUIStatus = newStatus.toUpperCase();
            }

            updateTableVisualStatus(order.getHotelId(), order.getTableNumber(), tableUIStatus);
        }
    }/**
     * 6. GENERAL STATUS UPDATE: Fallback for direct status changes.
     */
    public void updateOrderStatus(String orderId, String newStatus) {
        updateStatusWithChef(orderId, newStatus, null);
    }

    @Transactional
    public String checkoutOrders(String hotelId, CheckoutRequest request) {
        // 1. Fetch active orders from the kitchen collection
        List<KitchenOrder> activeOrders = kitchenOrderRepository.findAllById(request.getOrderIds());
        if (activeOrders.isEmpty()) {
            throw new RuntimeException("No active orders found to checkout for these IDs");
        }

        // 2. Calculate grand total and get current Indian Standard Time
        Double grandTotal = activeOrders.stream().mapToDouble(KitchenOrder::getTotalAmount).sum();
        ZonedDateTime nowIST = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));

        // 3. Build the Archive Document (CompletedOrder) for long-term history
        CompletedOrder finalBill = CompletedOrder.builder()
                .hotelId(hotelId)
                .orderType(activeOrders.get(0).getOrderType())
                .customerName(request.getCustomerName())
                .customerMobile(request.getCustomerMobile())
                .customerAddress(request.getCustomerAddress())
                .allOrders(activeOrders) // Keep full order history nested
                .grandTotal(grandTotal)
                .paymentStatus("PAID")
                .checkoutAt(nowIST.toLocalDateTime())
                .checkoutDate(nowIST.format(DateTimeFormatter.ISO_LOCAL_DATE))
                .checkoutTime(nowIST.format(DateTimeFormatter.ofPattern("HH:mm:ss")))
                .build();

        // 4. Save to the completed_orders collection
        CompletedOrder savedBill = completeOrderRepository.save(finalBill);

        // 5. ATOMIC CLEANUP: Only clear kitchen and reset table if save was successful
        if (savedBill.getId() != null) {
            // Remove from the 'Active' kitchen view
            kitchenOrderRepository.deleteAll(activeOrders);

            // Identify if this was a Table order
            Integer tableNum = activeOrders.get(0).getTableNumber();
            String orderType = activeOrders.get(0).getOrderType();

            if ("TABLE".equalsIgnoreCase(orderType) && tableNum != null) {

                // --- SYNC TABLE STATE ---
                // Set Status to INACTIVE (Available)
                updateTableVisualStatus(hotelId, tableNum, "INACTIVE");

                // Reset the Current Bill to 0.0 for the next guest
                updateTableBill(hotelId, tableNum, 0.0, true);

                log.info("CHECKOUT_COMPLETE: Hotel {} Table {} is now free and bill is reset.", hotelId, tableNum);
            }
        }

        return "Success";
    }
    // --- API 1: Paged Fetch (5 at a time) ---
    public Page<CompletedOrder> getCompletedOrdersPaged(String hotelId, int pageNumber) {
        Pageable pageable = PageRequest.of(pageNumber, 10, Sort.by("checkoutAt").descending());
        return completeOrderRepository.findByHotelId(hotelId, pageable);
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
                .orderId(order.getId())
                .date(order.getCheckoutDate())
                .time(order.getCheckoutTime())
                .orderType(order.getOrderType())
                .items(flattenedItems)
                .grandTotal(order.getGrandTotal())
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
            tableRepository.save(t);
        });
    }

    /**
     * FETCH TODAY'S COMPLETED HOME DELIVERIES (Clean Summary):
     * Returns only the essential fields for the dashboard list.
     */
    public List<DeliverySummaryDTO> getTodayCompletedHomeDeliveries(String hotelId) {
        ZonedDateTime nowIST = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
        String todayDate = nowIST.format(DateTimeFormatter.ISO_LOCAL_DATE);

        List<CompletedOrder> orders = completeOrderRepository
                .findByHotelIdAndOrderTypeAndCheckoutDateOrderByCheckoutAtDesc(
                        hotelId, "HOME_DELIVERY", todayDate);

        // Map Entity to DTO
        return orders.stream().map(order -> DeliverySummaryDTO.builder()
                        .id(order.getId())
                        .orderType(order.getOrderType())
                        .customerName(order.getCustomerName())
                        .customerMobile(order.getCustomerMobile())
                        .grandTotal(order.getGrandTotal())
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

        // 2. Fetch Metrics from respective Repositories

        // Table Stats: Tables where status is ACTIVE
        Long activeTables = tableRepository.countByHotelIdAndStatus(hotelId, "ACTIVE");

        // Delivery Stats: KitchenOrders where type is Delivery and status is PENDING
        Long pendingDeliveries = kitchenOrderRepository.countByHotelIdAndOrderTypeAndStatus(
                hotelId, "HOME_DELIVERY", "PENDING");

        // Employee Stats: Count all active employees (we will add 'onlineStatus' later)
        Long employeeCount = employeeRepository.countByHotelIdAndIsActive(hotelId, true);

        // Menu Item Stats: Count all items for this hotel
        Long totalItems = menuItemRepository.countByHotelId(hotelId);

        // 3. Financial/Archive Metrics (CompletedOrder Collection)

        // Completed Orders Today count
        Long completedTodayCount = completeOrderRepository.countByHotelIdAndCheckoutDate(
                hotelId, todayDate);

        // Todays Sales Rupees till now
        Double todaySalesRupees = completeOrderRepository.sumGrandTotalByHotelIdAndCheckoutDate(
                hotelId, todayDate);

        // Handle potential null from sum aggregation
        double finalTodaySales = todaySalesRupees != null ? todaySalesRupees : 0.0;

        // 4. Build and return the consolidated DTO
        return DashboardStatsDTO.builder()
                .activeTablesCount(activeTables)
                .pendingHomeDeliveriesCount(pendingDeliveries)
                .completedOrdersTodayCount(completedTodayCount)
                .employeeOnlineCount(employeeCount)
                .totalItemsCount(totalItems)
                .todaySalesRupees(finalTodaySales)
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
            tableRepository.save(table);
            log.info("BILL_SYNC: Hotel {} Table {} updated to {}", hotelId, tableNumber, table.getCurrentBill());
        });
    }
}