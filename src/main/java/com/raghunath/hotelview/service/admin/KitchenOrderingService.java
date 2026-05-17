package com.raghunath.hotelview.service.admin;

import com.raghunath.hotelview.entity.KitchenOrder;
import com.raghunath.hotelview.repository.KitchenOrderRepository;
import com.raghunath.hotelview.repository.TableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class KitchenOrderingService {

    private final KitchenOrderRepository kitchenOrderRepository;
    private final VersionService versionService;
    private final TableRepository tableRepository;

    /**
     * 1. UPDATE STATUS WITH CHEF: Handles Kitchen Lifecycle.
     * PREPARING -> Table shows 'PREPARING'
     * COMPLETED -> Table shows 'ACTIVE' (Food is served/Guest eating)
     */
    public void updateStatusWithChef(String orderId, String newStatus, String userId) {
        KitchenOrder order = kitchenOrderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        // Set updatedAt directly on entity and save
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
                && order.getTableName() != null) {

            String tableUIStatus = switch (newStatus.toUpperCase()) {
                case "ACCEPTED", "PREPARING" -> "ACCEPTED";
                case "COMPLETED" -> "ACTIVE";
                default -> newStatus.toUpperCase();
            };

            updateTableVisualStatus(order.getHotelId(),
                    order.getTableName(), tableUIStatus);
        }
    }

    private void updateTableVisualStatus(String hotelId, String tableName, String status) {
        if (tableName == null) return; // Never update table UI for delivery

        tableRepository.findByHotelIdAndTableName(hotelId, tableName).ifPresent(t -> {
            t.setStatus(status);
            t.setUpdatedAt(LocalDateTime.now());
            tableRepository.save(t);
        });
    }

    /**
     * GENERAL STATUS UPDATE: Fallback for direct status changes.
     */
    public void updateOrderStatus(String orderId, String newStatus) {
        updateStatusWithChef(orderId, newStatus, null);
    }

}
