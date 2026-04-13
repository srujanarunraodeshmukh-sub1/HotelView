package com.raghunath.hotelview.service.admin;

import com.raghunath.hotelview.entity.RestaurantTable;
import com.raghunath.hotelview.repository.TableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TableService {

    private final TableRepository tableRepository;

    public List<RestaurantTable> getAllTables(String hotelId) {
        return tableRepository.findAllByHotelIdOrderByTableNumberAsc(hotelId);
    }

    public RestaurantTable saveTable(String hotelId, RestaurantTable table) {
        table.setHotelId(hotelId);
        if (table.getStatus() == null) table.setStatus("INACTIVE");
        if (table.getCurrentBill() == null) table.setCurrentBill(0.0);
        table.setUpdatedAt(LocalDateTime.now()); // ✅ Set updatedAt
        return tableRepository.save(table);
    }

    public void deleteTable(String hotelId, int tableNumber) {
        tableRepository.findByHotelIdAndTableNumber(hotelId, tableNumber)
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

        if (details.getTableNumber() != null)
            existingTable.setTableNumber(details.getTableNumber());
        if (details.getSeatingCapacity() != null)
            existingTable.setSeatingCapacity(details.getSeatingCapacity());
        if (details.getStatus() != null)
            existingTable.setStatus(details.getStatus());

        existingTable.setUpdatedAt(LocalDateTime.now()); // ✅ Set updatedAt
        return tableRepository.save(existingTable);
    }

    public void deleteTable(String id, String hotelId) {
        RestaurantTable table = tableRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        "Table not found with id: " + id));

        if (!table.getHotelId().equals(hotelId)) {
            throw new RuntimeException(
                    "Unauthorized: You cannot delete this table");
        }

        if (!"INACTIVE".equalsIgnoreCase(table.getStatus())) {
            throw new RuntimeException(
                    "Cannot delete table while it is " + table.getStatus());
        }

        tableRepository.delete(table);
    }
}