package com.raghunath.hotelview.controller.admin;

import com.raghunath.hotelview.dto.admin.ReceiptSettingsDTO;
import com.raghunath.hotelview.service.ReceiptSettingsService;
import com.raghunath.hotelview.security.JwtUtil; // Replace with your exact project layout path to JwtUtil
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/receipt-settings")
@RequiredArgsConstructor
public class ReceiptSettingsController {

    private final ReceiptSettingsService service;
    private final JwtUtil jwtUtil;

    // --- TABLE & HOME RECEIPT ENDPOINTS ---

    @GetMapping("/table-home")
    public ResponseEntity<ReceiptSettingsDTO> getTableHomeSettings(@RequestHeader("Authorization") String authHeader) {
        String hotelId = jwtUtil.extractHotelId(authHeader.substring(7).trim());
        return ResponseEntity.ok(service.getSettings(hotelId, "TABLE_HOME"));
    }

    @PostMapping("/table-home")
    public ResponseEntity<String> saveTableHomeSettings(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody ReceiptSettingsDTO dto) {
        String hotelId = jwtUtil.extractHotelId(authHeader.substring(7).trim());
        return ResponseEntity.ok(service.saveSettings(hotelId, "TABLE_HOME", dto));
    }

    // --- INSTANT RECEIPT ENDPOINTS ---

    @GetMapping("/instant")
    public ResponseEntity<ReceiptSettingsDTO> getInstantSettings(@RequestHeader("Authorization") String authHeader) {
        String hotelId = jwtUtil.extractHotelId(authHeader.substring(7).trim());
        return ResponseEntity.ok(service.getSettings(hotelId, "INSTANT"));
    }

    @PostMapping("/instant")
    public ResponseEntity<String> saveInstantSettings(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody ReceiptSettingsDTO dto) {
        String hotelId = jwtUtil.extractHotelId(authHeader.substring(7).trim());
        return ResponseEntity.ok(service.saveSettings(hotelId, "INSTANT", dto));
    }
}