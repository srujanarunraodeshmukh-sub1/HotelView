package com.raghunath.hotelview.service.admin;

import com.raghunath.hotelview.dto.admin.CheckoutRequest;
import com.raghunath.hotelview.dto.admin.OrderItem;
import com.raghunath.hotelview.entity.CompletedOrder;
import com.raghunath.hotelview.entity.KitchenOrder;
import com.raghunath.hotelview.entity.OrderDraft;
import com.raghunath.hotelview.repository.CompleteOrderRepository;
import com.raghunath.hotelview.repository.KitchenOrderRepository;
import com.raghunath.hotelview.repository.OrderDraftRepository;
import com.raghunath.hotelview.repository.TableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderDraftRepository draftRepository;
    private final TableRepository tableRepository;
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

        // Use the helper only for TABLE orders
        updateTableVisualStatus(hotelId, tableNumber, "PENDING");
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

        // Debug log to see what Java sees before the update
        log.info("Updating Order: ID={}, Type={}, Table={}", order.getId(), order.getOrderType(), order.getTableNumber());

        Query query = new Query(Criteria.where("id").is(orderId));
        Update update = new Update();
        update.set("status", newStatus.toUpperCase());

        if ("PREPARING".equalsIgnoreCase(newStatus)) {
            update.set("acceptedBy", userId);
        }

        mongoTemplate.updateFirst(query, update, KitchenOrder.class);

        // STRICT GUARD: Only enter if it is explicitly a TABLE order and tableNumber is NOT null and NOT 0
        if ("TABLE".equalsIgnoreCase(order.getOrderType()) &&
                order.getTableNumber() != null &&
                order.getTableNumber() > 0) {

            String tableUIStatus = "COMPLETED".equalsIgnoreCase(newStatus) ? "ACTIVE" : newStatus.toUpperCase();
            updateTableVisualStatus(order.getHotelId(), order.getTableNumber(), tableUIStatus);
        } else {
            log.info("SKIPPING_UI_UPDATE: Home Delivery or Invalid Table Number detected.");
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
        // 1. Fetch all documents for the provided IDs
        List<KitchenOrder> activeOrders = kitchenOrderRepository.findAllById(request.getOrderIds());

        if (activeOrders.isEmpty()) throw new RuntimeException("No orders found to checkout");

        // 2. Calculate Grand Total
        Double grandTotal = activeOrders.stream()
                .mapToDouble(KitchenOrder::getTotalAmount)
                .sum();

        // 3. Prepare IST Time
        ZonedDateTime nowIST = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));

        // 4. Create the Archive Document
        CompletedOrder finalBill = CompletedOrder.builder()
                .hotelId(hotelId)
                .orderType(activeOrders.get(0).getOrderType()) // Detects TABLE or DELIVERY
                .customerName(request.getCustomerName())
                .customerMobile(request.getCustomerMobile())
                .customerAddress(request.getCustomerAddress())
                .allOrders(activeOrders) // 👈 Nests the full documents
                .grandTotal(grandTotal)
                .paymentStatus("PAID")
                .checkoutAt(nowIST.toLocalDateTime())
                .checkoutDate(nowIST.format(DateTimeFormatter.ISO_LOCAL_DATE))
                .checkoutTime(nowIST.format(DateTimeFormatter.ofPattern("HH:mm:ss")))
                .build();

        // 5. Save to NEW collection
        completeOrderRepository.save(finalBill);

        // 6. DELETE from Active Kitchen collection (Clean up)
        kitchenOrderRepository.deleteAll(activeOrders);

        // 7. Reset Table status if it was a table order
        if (activeOrders.get(0).getTableNumber() != null) {
            updateTableVisualStatus(hotelId, activeOrders.get(0).getTableNumber(), "AVAILABLE");
        }

        return "Checkout complete. Bill ID: " + finalBill.getId();
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
}