package com.raghunath.hotelview.controller.admin;

import com.raghunath.hotelview.dto.admin.*;
import com.raghunath.hotelview.entity.CompletedOrder;
import com.raghunath.hotelview.entity.KitchenOrder;
import com.raghunath.hotelview.entity.OrderDraft;
import com.raghunath.hotelview.entity.OrderEdit;
import com.raghunath.hotelview.repository.KitchenOrderRepository;
import com.raghunath.hotelview.service.admin.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;
    private final KitchenOrderRepository kitchenOrderRepository;
    private final MongoTemplate mongoTemplate;

    private String getAuthenticatedUserId() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    // 1. SAVE DRAFT FOR SPECIFIC TABLE
    @PostMapping("/draft/{tableName}")
    public ResponseEntity<String> saveOrderDraft(@PathVariable String tableName, @Valid @RequestBody List<OrderItem> items){
        String hotelId = SecurityContextHolder.getContext().getAuthentication().getName();
        orderService.saveDraft(hotelId, tableName, items);
        return ResponseEntity.ok("Draft saved successfully");
    }

    // 2. GET DRAFT FOR SPECIFIC TABLE
    @GetMapping("/draft/{tableName}")
    public OrderDraft getOrderDraft(@PathVariable String tableName){
        String hotelId = SecurityContextHolder.getContext().getAuthentication().getName();
        return orderService.getDraft(hotelId, tableName);
    }

    // 3. PLACE TABLE ORDER
    @PostMapping("/confirm/{tableName}")
    public ResponseEntity<String> confirmOrder(@PathVariable String tableName, @Valid @RequestBody OrderRequest request
    ) {
        String hotelId = SecurityContextHolder.getContext().getAuthentication().getName();
        String waiterId = getAuthenticatedUserId();

        orderService.confirmOrder(
                hotelId,
                tableName,
                request.getItems(),
                waiterId,
                request.getComment()
        );
        return ResponseEntity.ok("Order sent to kitchen");
    }

    // 4. PLACE HOME DELIVERY ORDER
    @PostMapping("/confirm/delivery")
    public ResponseEntity<String> confirmHomeDelivery(@Valid @RequestBody DeliveryRequest request) {
        String hotelId = SecurityContextHolder.getContext().getAuthentication().getName();
        String waiterId = getAuthenticatedUserId();

        // Default to HOME_DELIVERY if user sends null/empty
        String type = StringUtils.hasText(request.getOrderType()) ? request.getOrderType() : "HOME_DELIVERY";

        String orderId = orderService.confirmHomeDelivery(hotelId, request.getItems(), waiterId, type);
        return ResponseEntity.ok("Order sent to kitchen. ID: " + orderId);
    }

    // 5. FETCH SPECIFIC TABLE ORDERS (Latest First)
    @GetMapping("/table/{tableName}")
    public ResponseEntity<List<KitchenOrder>> getTableOrders(@PathVariable String tableName) {
        String hotelId = SecurityContextHolder.getContext().getAuthentication().getName();
        // Fetches all orders for this table that are not yet PAID, newest at top
        return ResponseEntity.ok(orderService.getOrdersByTable(hotelId, tableName));
    }

    // 6. CHECKOUT ORDER FOR TABLE
    @PostMapping("/checkout")
    public ResponseEntity<CheckoutResponse> checkout(@RequestBody CheckoutRequest request) {
        // 1. Extract hotelId from Token
        String hotelId = SecurityContextHolder.getContext().getAuthentication().getName();

        // 2. Call service which now returns the full CheckoutResponse object
        CheckoutResponse response = orderService.checkoutOrders(hotelId, request);

        // 3. Return the object as JSON
        return ResponseEntity.ok(response);
    }

    // 7. GET STATISTICS
    @GetMapping("/dashboard/stats")
    public ResponseEntity<DashboardStatsDTO> getDashboardStats() {
        String hotelId = getAuthenticatedUserId();
        return ResponseEntity.ok(orderService.getDashboardStats(hotelId));
    }

    // 8. EDIT ORDER
    @PutMapping("/kitchen/{orderId}/confirm-edit")
    public ResponseEntity<String> confirmEdit(
            @PathVariable String orderId,
            @RequestBody List<OrderItem> newItems) {
        String hotelId = SecurityContextHolder.getContext().getAuthentication().getName();
        orderService.confirmOrderEdit(hotelId, orderId, newItems);
        return ResponseEntity.ok("Order updated and logs saved successfully");
    }

    // 9. Get Full Table History via Completed Order ID
    @GetMapping("/summary/completed/{completedOrderId}")
    public ResponseEntity<Map<String, Object>> getFullTableSummary(@PathVariable String completedOrderId) {
        String hotelId = SecurityContextHolder.getContext().getAuthentication().getName();

        try {
            Map<String, Object> aggregatedSummary = orderService.getAggregatedTableSummary(hotelId, completedOrderId);
            return ResponseEntity.ok(aggregatedSummary);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }


    // 1. PUBLIC WEBHOOK: Receives orders from Zomato/Swiggy
    // This is called automatically by the external platform
    @PostMapping("/webhook")
    public ResponseEntity<String> receiveExternalOrder(@RequestBody OrderWebhookDTO externalOrder) {
        orderService.processExternalOrder(externalOrder);
        return ResponseEntity.ok("Order Received Successfully");
    }

    // 2. PRIVATE API: Called by your Admin/Waiter from the Dashboard
    // This moves the order from 'ExternalOrder' to 'KitchenOrder'
    @PutMapping("/external/accept/{orderId}")
    public ResponseEntity<String> acceptExternalOrder(@PathVariable String orderId) {
        // Extracts the ID from the JWT token
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();

        // Calls the second service method to move data to the kitchen
        String kitchenOrderId = orderService.approveExternalOrder(orderId, userId);

        return ResponseEntity.ok("External order approved. Kitchen Order ID: " + kitchenOrderId);
    }
}
