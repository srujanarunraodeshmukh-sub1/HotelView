package com.raghunath.hotelview.service.admin;

import com.raghunath.hotelview.entity.KitchenOrder;
import com.raghunath.hotelview.repository.KitchenOrderRepository;
import com.raghunath.hotelview.repository.TableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class KitchenOrderingService {

    private final KitchenOrderRepository kitchenOrderRepository;
    private final VersionService versionService;
    private final TableRepository tableRepository;

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    /**
     * 1. UPDATE STATUS WITH CHEF / ADMIN: Handles Kitchen Lifecycle.
     * Open to both Chefs and Admins natively.
     */
    @Transactional
    public void updateStatusWithChef(String orderId, String newStatus, String userId) {
        KitchenOrder order = kitchenOrderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        String formattedStatus = newStatus.toUpperCase();
        order.setStatus(formattedStatus);
        order.setUpdatedAt(ZonedDateTime.now(IST).toLocalDateTime());

        // Tracks who claimed/accepted the order preparation ticket (Chef or Admin)
        if ("PREPARING".equalsIgnoreCase(formattedStatus) && userId != null) {
            order.setAcceptedBy(userId);
            log.info("Order {} accepted by User: {}", orderId, userId);
        }

        // ADD THIS BLOCK
        if ("COMPLETED".equalsIgnoreCase(formattedStatus) && userId != null) {
            order.setCompletedBy(userId);
            log.info("Order {} completed by User: {}", orderId, userId);
        }

        kitchenOrderRepository.save(order);

        // Notify downstream apps via version updates
        versionService.bumpKitchen(order.getHotelId());

        // Process Live Floor Table Visual State Transitions
        if ("TABLE".equalsIgnoreCase(order.getOrderType()) && order.getTableName() != null) {
            String tableUIStatus = switch (formattedStatus) {
                case "ACCEPTED", "PREPARING" -> "ACCEPTED";
                case "COMPLETED" -> "ACTIVE";
                default -> formattedStatus;
            };

            updateTableVisualStatus(order.getHotelId(), order.getTableName(), tableUIStatus);
        }
    }

    /**
     * ✅ Untouched: Keeps your original string-basedTableName logic intact
     */
    private void updateTableVisualStatus(String hotelId, String tableName, String status) {
        if (tableName == null) return;

        tableRepository.findByHotelIdAndTableName(hotelId, tableName).ifPresent(t -> {
            t.setStatus(status);
            t.setUpdatedAt(ZonedDateTime.now(IST).toLocalDateTime());
            tableRepository.save(t);

            // Sync live dashboard views
            versionService.bumpTables(hotelId);
        });
    }

    /**
     * GENERAL STATUS UPDATE: Fallback for direct status modifications
     */
    @Transactional
    public void updateOrderStatus(String orderId, String newStatus) {
        updateStatusWithChef(orderId, newStatus, null);
    }
}