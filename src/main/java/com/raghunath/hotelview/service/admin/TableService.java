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
        if (table.getStatus() == null) table.setStatus("Vacant");
        if (table.getCurrentBill() == null) table.setCurrentBill(0.0);
        return tableRepository.save(table);
    }

    // Bulk delete (if a hotel renovates and removes tables)
    public void deleteTable(String hotelId, int tableNumber) {
        tableRepository.findByHotelIdAndTableNumber(hotelId, tableNumber)
                .ifPresent(tableRepository::delete);
    }
}