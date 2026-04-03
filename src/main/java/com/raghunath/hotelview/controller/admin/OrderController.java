package com.raghunath.hotelview.controller.admin;

import com.raghunath.hotelview.dto.admin.CheckoutRequest;
import com.raghunath.hotelview.dto.admin.DashboardStatsDTO;
import com.raghunath.hotelview.dto.admin.OrderItem;
import com.raghunath.hotelview.entity.CompletedOrder;
import com.raghunath.hotelview.entity.KitchenOrder;
import com.raghunath.hotelview.entity.OrderDraft;
import com.raghunath.hotelview.repository.KitchenOrderRepository;
import com.raghunath.hotelview.service.admin.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;
    private final KitchenOrderRepository kitchenOrderRepository;

    private String getAuthenticatedUserId() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    @PostMapping("/draft/{tableNumber}")
    public ResponseEntity<String> saveOrderDraft(@PathVariable int tableNumber, @Valid @RequestBody List<OrderItem> items){
        String hotelId = SecurityContextHolder.getContext().getAuthentication().getName();
        orderService.saveDraft(hotelId, tableNumber, items);
        return ResponseEntity.ok("Draft saved successfully");
    }

    @GetMapping("/draft/{tableNumber}")
    public OrderDraft getOrderDraft(@PathVariable int tableNumber){
        String hotelId = SecurityContextHolder.getContext().getAuthentication().getName();
        return orderService.getDraft(hotelId, tableNumber);
    }

    // 1. PLACE ORDER (Waiter/Admin clicks 'Confirm')
    // 1. PLACE ORDER (Updated to accept current items from UI)
    // 1. PLACE TABLE ORDER
    @PostMapping("/confirm/{tableNumber}")
    public ResponseEntity<String> confirmOrder(@PathVariable int tableNumber, @Valid @RequestBody List<OrderItem> items) {
        String hotelId = SecurityContextHolder.getContext().getAuthentication().getName();
        String waiterId = getAuthenticatedUserId();
        return ResponseEntity.ok(orderService.confirmOrder(hotelId, tableNumber, items, waiterId));
    }

    // 2. PLACE HOME DELIVERY ORDER
    @PostMapping("/confirm/delivery")
    public ResponseEntity<String> confirmHomeDelivery(@Valid @RequestBody List<OrderItem> items) {
        String hotelId = SecurityContextHolder.getContext().getAuthentication().getName();
        String waiterId = getAuthenticatedUserId();

        String orderId = orderService.confirmHomeDelivery(hotelId, items, waiterId);
        return ResponseEntity.ok("Home delivery order sent to kitchen. ID: " + orderId);
    }

    // 4. FETCH SPECIFIC TABLE ORDERS (Latest First)
    @GetMapping("/table/{tableNumber}")
    public ResponseEntity<List<KitchenOrder>> getTableOrders(@PathVariable int tableNumber) {
        String hotelId = SecurityContextHolder.getContext().getAuthentication().getName();
        // Fetches all orders for this table that are not yet PAID, newest at top
        return ResponseEntity.ok(orderService.getOrdersByTable(hotelId, tableNumber));
    }

    @PostMapping("/checkout")
    public ResponseEntity<String> checkout(@RequestBody CheckoutRequest request) {
        String hotelId = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(orderService.checkoutOrders(hotelId, request));
    }

    // 5. CHEF: FETCH PENDING ORDERS (New orders only)
    @GetMapping("/kitchen/pending")
    public ResponseEntity<List<KitchenOrder>> getPendingOrders() {
        String hotelId = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(kitchenOrderRepository.findByHotelIdAndStatus(hotelId, "PENDING"));
    }

    // 6. CHEF: ACCEPT ORDER (Change PENDING -> PREPARING)
    @PatchMapping("/kitchen/accept/{orderId}")
    public ResponseEntity<String> acceptOrder(@PathVariable String orderId) {
        String chefId = getAuthenticatedUserId();
        orderService.updateStatusWithChef(orderId, "PREPARING", chefId);
        return ResponseEntity.ok("Accepted by " + chefId);
    }

    // 7. CHEF: FETCH PREPARING ORDERS (Orders currently being cooked)
    @GetMapping("/kitchen/preparing")
    public ResponseEntity<List<KitchenOrder>> getPreparingOrders() {
        String hotelId = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(kitchenOrderRepository.findByHotelIdAndStatus(hotelId, "PREPARING"));
    }

    // 8. CHEF: COMPLETE ORDER (Change PREPARING -> COMPLETED)
    @PatchMapping("/kitchen/complete/{orderId}")
    public ResponseEntity<String> completeOrder(@PathVariable String orderId) {
        orderService.updateOrderStatus(orderId, "COMPLETED");
        return ResponseEntity.ok("Order marked as COMPLETED");
    }

    @GetMapping("/dashboard/stats")
    public ResponseEntity<DashboardStatsDTO> getDashboardStats() {
        String hotelId = getAuthenticatedUserId();
        return ResponseEntity.ok(orderService.getDashboardStats(hotelId));
    }

    // 9. FETCH COMPLETED ORDERS (Ready to be served)
    @GetMapping("/kitchen/completed")
    public ResponseEntity<List<KitchenOrder>> getCompletedOrders() {
        String hotelId = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(kitchenOrderRepository.findByHotelIdAndStatus(hotelId, "COMPLETED"));
    }
}
