package com.raghunath.hotelview.service.admin;

import com.raghunath.hotelview.entity.KitchenOrder;
import com.raghunath.hotelview.entity.RestaurantTable;
import com.raghunath.hotelview.repository.KitchenOrderRepository;
import com.raghunath.hotelview.repository.TableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TableService {

    private final TableRepository tableRepository;
    private final KitchenOrderRepository kitchenOrderRepository;
    private final VersionService versionService;

    public List<RestaurantTable> getAllTables(String hotelId) {
        return tableRepository.findAllByHotelIdOrderByTableNameAsc(hotelId);
    }

    public RestaurantTable saveTable(String hotelId, RestaurantTable table) {
        table.setHotelId(hotelId);
        if (table.getStatus() == null) table.setStatus("INACTIVE");
        if (table.getCurrentBill() == null) table.setCurrentBill(0.0);
        table.setUpdatedAt(LocalDateTime.now()); // ✅ Set updatedAt
        return tableRepository.save(table);
    }

    @Transactional
    public void transferTableOrders(String hotelId, String fromTable, String toTable) {
        // 1. Validate that the destination table exists
        RestaurantTable targetTable = tableRepository.findByHotelIdAndTableName(hotelId, toTable)
                .orElseThrow(() -> new RuntimeException("Target table " + toTable + " does not exist"));

        // 2. Fetch all active kitchen orders for the source table
        List<KitchenOrder> activeOrders = kitchenOrderRepository.findByHotelIdAndTableName(hotelId, fromTable);

        if (activeOrders.isEmpty()) {
            throw new RuntimeException("No active orders found on Table " + fromTable);
        }

        // 3. Calculate the total amount being moved
        double transferAmount = activeOrders.stream()
                .mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0.0)
                .sum();

        // 4. Update the table number in each kitchen order
        activeOrders.forEach(order -> {
            order.setTableName(toTable);
            // Optional: you can also update the 'updatedAt' timestamp here
        });
        kitchenOrderRepository.saveAll(activeOrders);

        // 5. Update Source Table: Deduct bill and set to AVAILABLE if empty
        tableRepository.findByHotelIdAndTableName(hotelId, fromTable).ifPresent(source -> {
            double current = source.getCurrentBill() != null ? source.getCurrentBill() : 0.0;
            double newBill = Math.max(0, current - transferAmount);
            source.setCurrentBill(newBill);
            if (newBill <= 0) source.setStatus("AVAILABLE");
            tableRepository.save(source);
        });

        // 6. Update Target Table: Add bill and set to OCCUPIED/PENDING
        double targetCurrent = targetTable.getCurrentBill() != null ? targetTable.getCurrentBill() : 0.0;
        targetTable.setCurrentBill(targetCurrent + transferAmount);
        targetTable.setStatus("PENDING"); // Or "OCCUPIED" based on your logic
        tableRepository.save(targetTable);

        // 7. Sync Versions so Waiter Apps update immediately
        versionService.bumpTables(hotelId);
    }



    public void deleteTable(String hotelId, String tableName) {
        tableRepository.findByHotelIdAndTableName(hotelId, tableName)
                .ifPresent(tableRepository::delete);
    }

    public RestaurantTable updateTable(String id, String hotelId,
                                       RestaurantTable details) {
        RestaurantTable existingTable = tableRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        "Table not found with id: " + id));

        if (!existingTable.getHotelId().equals(hotelId)) {
            throw new RuntimeException(
                    "Unauthorized: This table does not belong to your hotel");
        }

        if (details.getTableName() != null)
            existingTable.setTableName(details.getTableName());
        if (details.getSeatingCapacity() != null)
            existingTable.setSeatingCapacity(details.getSeatingCapacity());
        if (details.getStatus() != null)
            existingTable.setStatus(details.getStatus());

        existingTable.setUpdatedAt(LocalDateTime.now()); // ✅ Set updatedAt
        return tableRepository.save(existingTable);
    }

//    public void deleteTable(String id, String hotelId) {
//        RestaurantTable table = tableRepository.findById(id)
//                .orElseThrow(() -> new RuntimeException(
//                        "Table not found with id: " + id));
//
//        if (!table.getHotelId().equals(hotelId)) {
//            throw new RuntimeException(
//                    "Unauthorized: You cannot delete this table");
//        }
//
//        if (!"INACTIVE".equalsIgnoreCase(table.getStatus())) {
//            throw new RuntimeException(
//                    "Cannot delete table while it is " + table.getStatus());
//        }
//
//        tableRepository.delete(table);
//  }
}