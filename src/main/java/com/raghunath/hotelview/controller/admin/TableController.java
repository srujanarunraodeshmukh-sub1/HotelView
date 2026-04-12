package com.raghunath.hotelview.controller.admin;

import com.raghunath.hotelview.entity.RestaurantTable;
import com.raghunath.hotelview.service.admin.TableService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tables")
@RequiredArgsConstructor
public class TableController {

    private final TableService tableService;

    // 1. Get all tables for the logged-in Hotel
    @GetMapping
    public List<RestaurantTable> getMyTables() {
        String hotelId = SecurityContextHolder.getContext().getAuthentication().getName();
        return tableService.getAllTables(hotelId);
    }

    // 2. Add a new table manually
    @PostMapping("/add")
    public ResponseEntity<RestaurantTable> addTable(@RequestBody RestaurantTable table) {
        String hotelId = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(tableService.saveTable(hotelId, table));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RestaurantTable> updateTable(
            @PathVariable String id,
            @RequestBody RestaurantTable tableDetails) {
        String hotelId = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(tableService.updateTable(id, hotelId, tableDetails));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteTable(@PathVariable String id) {
        String hotelId = SecurityContextHolder.getContext().getAuthentication().getName();
        tableService.deleteTable(id, hotelId);
        return ResponseEntity.ok("Table deleted successfully");
    }

}