package com.raghunath.hotelview.controller.admin;

import com.raghunath.hotelview.dto.admin.*;
import com.raghunath.hotelview.entity.CompletedOrder;
import com.raghunath.hotelview.entity.KitchenOrder;
import com.raghunath.hotelview.entity.OrderDraft;
import com.raghunath.hotelview.entity.OrderEdit;
import com.raghunath.hotelview.repository.KitchenOrderRepository;
import com.raghunath.hotelview.security.JwtUtil;
import com.raghunath.hotelview.service.admin.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;
    private final KitchenOrderRepository kitchenOrderRepository;
    private final MongoTemplate mongoTemplate;
    private final JwtUtil jwtUtil;

    private String getAuthenticatedUserId() {
        try {
            // 1. Grab the active HTTP request from the thread context
            jakarta.servlet.http.HttpServletRequest request =
                    ((org.springframework.web.context.request.ServletRequestAttributes)
                            org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes())
                            .getRequest();

            // 2. Fetch the Authorization header
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7).trim();

                // 3. Extract the subject (userId) using your verified JwtUtil structure
                return jwtUtil.extractUserId(token);
            }
        } catch (Exception e) {
            // Log the failure fallback info silently
        }

        // Fallback default: matches your security context subject name if header reading fails
        return org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication().getName();
    }

    // 1. SAVE DRAFT FOR SPECIFIC TABLE
    @PostMapping("/draft/{tableName}")
    public ResponseEntity<String> saveOrderDraft(@PathVariable String tableName, @Valid @RequestBody List<OrderItem> items) {
        String hotelId = SecurityContextHolder.getContext().getAuthentication().getName();
        orderService.saveDraft(hotelId, tableName, items);
        return ResponseEntity.ok("Draft saved successfully");
    }

    // 2. GET DRAFT FOR SPECIFIC TABLE
    @GetMapping("/draft/{tableName}")
    public OrderDraft getOrderDraft(@PathVariable String tableName) {
        String hotelId = SecurityContextHolder.getContext().getAuthentication().getName();
        return orderService.getDraft(hotelId, tableName);
    }

    // 3. PLACE TABLE ORDER
    @PostMapping("/confirm/{tableName}")
    public ResponseEntity<String> confirmOrder(@PathVariable String tableName, @Valid @RequestBody OrderRequest request) {
        String hotelId = SecurityContextHolder.getContext().getAuthentication().getName();
        String waiterId = getAuthenticatedUserId();

        orderService.confirmOrder(hotelId, tableName, request.getItems(), waiterId, request.getComment());
        return ResponseEntity.ok("Order sent to kitchen");
    }

    // 4. PLACE HOME DELIVERY ORDER
    @PostMapping("/confirm/delivery")
    public ResponseEntity<String> confirmHomeDelivery(@Valid @RequestBody DeliveryRequest request) {
        String hotelId = SecurityContextHolder.getContext().getAuthentication().getName();
        String waiterId = getAuthenticatedUserId();

        String type = StringUtils.hasText(request.getOrderType()) ? request.getOrderType() : "HOME_DELIVERY";

        String orderId = orderService.confirmHomeDelivery(hotelId, request.getItems(), waiterId, type);
        return ResponseEntity.ok("Order sent to kitchen. ID: " + orderId);
    }

    // 5. FETCH SPECIFIC TABLE ORDERS (Latest First)
    @GetMapping("/table/{tableName}")
    public ResponseEntity<List<KitchenOrder>> getTableOrders(@PathVariable String tableName) {
        String hotelId = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(orderService.getOrdersByTable(hotelId, tableName));
    }

    // 6. CHECKOUT ORDER FOR TABLE
    @PostMapping("/checkout")
    public ResponseEntity<CheckoutResponse> checkout(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody CheckoutRequest request) {

        // Extract token
        String token = authHeader.substring(7).trim();

        // Extract hotelId and actual user id from JWT
        String hotelId = jwtUtil.extractHotelId(token);
        String checkoutBy = jwtUtil.extractUserId(token);

        CheckoutResponse response = orderService.checkoutOrders(hotelId, request, checkoutBy);

        return ResponseEntity.ok(response);
    }

    // 7. INSTANT CHECKOUT
    @PostMapping("/instant/checkout")
    public ResponseEntity<CheckoutResponse> processInstantOrder(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody InstantCheckoutRequest request) {

        String token = authHeader.substring(7).trim();

        String hotelId = jwtUtil.extractHotelId(token);
        String actualLoggedInUser = jwtUtil.extractUserId(token);

        CheckoutResponse response = orderService.instantOrderAndCheckout(hotelId, request, actualLoggedInUser);

        return ResponseEntity.ok(response);
    }

    // 8. GET STATISTICS
    @GetMapping("/dashboard/stats")
    public ResponseEntity<DashboardStatsDTO> getDashboardStats() {
        String hotelId = SecurityContextHolder.getContext().getAuthentication().getName(); // ← fix
        return ResponseEntity.ok(orderService.getDashboardStats(hotelId));
    }

    // 9. EDIT ORDER
    @PutMapping("/kitchen/{orderId}/confirm-edit")
    public ResponseEntity<String> confirmEdit(
            @PathVariable String orderId,
            @RequestBody List<OrderItem> newItems) {
        String hotelId = SecurityContextHolder.getContext().getAuthentication().getName();
        orderService.confirmOrderEdit(hotelId, orderId, newItems);
        return ResponseEntity.ok("Order updated and logs saved successfully");
    }

    // 10. Get Full Table History via Completed Order ID
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

    // 11. PUBLIC WEBHOOK: Receives orders from Zomato/Swiggy
    @PostMapping("/webhook")
    public ResponseEntity<String> receiveExternalOrder(@RequestBody OrderWebhookDTO externalOrder) {
        orderService.processExternalOrder(externalOrder);
        return ResponseEntity.ok("Order Received Successfully");
    }

    // 12. PRIVATE API: Approve external order and move to kitchen
    @PutMapping("/external/accept/{orderId}")
    public ResponseEntity<String> acceptExternalOrder(@PathVariable String orderId) {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        String kitchenOrderId = orderService.approveExternalOrder(orderId, userId);
        return ResponseEntity.ok("External order approved. Kitchen Order ID: " + kitchenOrderId);
    }
}
