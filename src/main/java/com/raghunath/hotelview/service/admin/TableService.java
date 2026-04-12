package com.raghunath.hotelview.service.admin;

import com.raghunath.hotelview.entity.RestaurantTable;
import com.raghunath.hotelview.repository.TableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TableService {

    private final TableRepository tableRepository;

    // Fetch all tables for the Admin dashboard
    public List<RestaurantTable> getAllTables(String hotelId) {
        return tableRepository.findAllByHotelIdOrderByTableNumberAsc(hotelId);
    }

    // Add/Update a single table
    public RestaurantTable saveTable(String hotelId, RestaurantTable table) {
        table.setHotelId(hotelId);
        // Default values for new tables
        if (table.getStatus() == null) table.setStatus("InActive");
        if (table.getCurrentBill() == null) table.setCurrentBill(0.0);
        return tableRepository.save(table);
    }

    // Bulk delete (if a hotel renovates and removes tables)
    public void deleteTable(String hotelId, int tableNumber) {
        tableRepository.findByHotelIdAndTableNumber(hotelId, tableNumber)
                .ifPresent(tableRepository::delete);
    }

    public RestaurantTable updateTable(String id, String hotelId, RestaurantTable details) {
        RestaurantTable existingTable = tableRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Table not found with id: " + id));

        // Security Check: Ensure table belongs to the logged-in hotel
        if (!existingTable.getHotelId().equals(hotelId)) {
            throw new RuntimeException("Unauthorized: This table does not belong to your hotel");
        }

        // Update fields
        if (details.getTableNumber() != null) existingTable.setTableNumber(details.getTableNumber());
        if (details.getSeatingCapacity() != null) existingTable.setSeatingCapacity(details.getSeatingCapacity());
        if (details.getStatus() != null) existingTable.setStatus(details.getStatus());

        return tableRepository.save(existingTable);
    }

    public void deleteTable(String id, String hotelId) {
        RestaurantTable table = tableRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Table not found with id: " + id));

        // Security Check
        if (!table.getHotelId().equals(hotelId)) {
            throw new RuntimeException("Unauthorized: You cannot delete this table");
        }

        // Production Safety Check: Don't delete if the table is currently occupied
        if (!"INACTIVE".equalsIgnoreCase(table.getStatus())) {
            throw new RuntimeException("Cannot delete table while it is " + table.getStatus());
        }

        tableRepository.delete(table);
    }
}