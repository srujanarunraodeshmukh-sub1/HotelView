package com.raghunath.hotelview.controller.admin;

import com.raghunath.hotelview.entity.CompletedOrder;
import com.raghunath.hotelview.repository.CompleteOrderRepository;
import com.raghunath.hotelview.service.admin.VersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/sync")
@RequiredArgsConstructor
public class SalesSyncController {

    private final VersionService versionService;
    private final CompleteOrderRepository orderRepo;

    private String getHotelId() {
        return SecurityContextHolder.getContext()
                .getAuthentication().getName();
    }

    // ─────────────────────────────────────────────────────
    // SALES API 1: Version (timestamp) + Count
    // GET /api/v1/sync/sales/version
    //
    // Krishna logic:
    // if serverVersion == localVersion → do nothing
    // if serverVersion > localVersion  → call /sales/delta?since={localVersion}
    // after delta: if serverCount != localCount → call full sales API
    // ─────────────────────────────────────────────────────
    @GetMapping("/sales/version")
    public ResponseEntity<Map<String, Object>> getSalesVersion() {
        String hotelId = getHotelId();
        String todayDate = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"))
                .format(DateTimeFormatter.ISO_LOCAL_DATE);

        long version = versionService.getSalesVersion(hotelId);

        // Auto-initialize if never bumped before
        if (version == 0L) {
            versionService.bumpSales(hotelId);
            versionService.bumpTables(hotelId);
            versionService.bumpKitchen(hotelId);
            version = versionService.getSalesVersion(hotelId);
        }

        long todayCount = orderRepo.countByHotelIdAndCheckoutDate(hotelId, todayDate);
        long totalCount = orderRepo.countByHotelId(hotelId); // ✅ All time total

        return ResponseEntity.ok(Map.of(
                "version", version,
                "todayCount", todayCount,
                "totalCount", totalCount,  // ✅ Added
                "date", todayDate
        ));
    }

    // ─────────────────────────────────────────────────────
    // SALES API 2: Delta since timestamp
    // GET /api/v1/sync/sales/delta?since=1746057600000
    //
    // Krishna passes his localVersion (timestamp millis) as since
    // Gets only orders modified after that timestamp
    // ─────────────────────────────────────────────────────
    @GetMapping("/sales/delta")
    public ResponseEntity<Map<String, Object>> getSalesDelta(
            @RequestParam long since) {  // ✅ long instead of String

        String hotelId = getHotelId();
        String todayDate = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"))
                .format(DateTimeFormatter.ISO_LOCAL_DATE);

        // ✅ Convert millis timestamp to LocalDateTime
        LocalDateTime sinceTime = Instant.ofEpochMilli(since)
                .atZone(ZoneId.of("Asia/Kolkata"))
                .toLocalDateTime();

        List<CompletedOrder> deltaOrders =
                orderRepo.findByHotelIdAndLastModifiedAfterOrderByLastModifiedDesc(
                        hotelId, sinceTime);

        long serverCount = orderRepo.countByHotelIdAndCheckoutDate(hotelId, todayDate);
        long totalCount = orderRepo.countByHotelId(hotelId);

        return ResponseEntity.ok(Map.of(
                "deltaOrders", deltaOrders,
                "serverCount", serverCount,
                "totalCount", totalCount,
                "date", todayDate
        ));
    }

    // ─────────────────────────────────────────────────────
    // TABLE API 1: Version (timestamp) + Count
    // GET /api/v1/sync/tables/version
    // ─────────────────────────────────────────────────────
    @GetMapping("/tables/version")
    public ResponseEntity<Map<String, Object>> getTableVersion() {
        String hotelId = getHotelId();
        long version = versionService.getTableVersion(hotelId);
        long count = versionService.getTableCount(hotelId);

        return ResponseEntity.ok(Map.of(
                "version", version,     // timestamp millis
                "totalCount", count     // total tables count
        ));
    }

    // ─────────────────────────────────────────────────────
    // TABLE API 2: Delta since timestamp
    // GET /api/v1/sync/tables/delta?since=1746057600000
    // ─────────────────────────────────────────────────────
    @GetMapping("/tables/delta")
    public ResponseEntity<Map<String, Object>> getTableDelta(
            @RequestParam long since) {

        String hotelId = getHotelId();

        LocalDateTime sinceTime = Instant.ofEpochMilli(since)
                .atZone(ZoneId.of("Asia/Kolkata"))
                .toLocalDateTime();

        List<?> deltaTables = versionService.getTablesDeltaSince(
                hotelId, sinceTime);
        long totalCount = versionService.getTableCount(hotelId);

        return ResponseEntity.ok(Map.of(
                "deltaTables", deltaTables,  // only changed tables
                "totalCount", totalCount     // total count for verification
        ));
    }

    // ─────────────────────────────────────────────────────
    // KITCHEN API 1: Version (timestamp) + Count for table
    // GET /api/v1/sync/kitchen/version?tableNumber=3
    // ─────────────────────────────────────────────────────
    @GetMapping("/kitchen/version")
    public ResponseEntity<Map<String, Object>> getKitchenVersion(
            @RequestParam int tableNumber) {

        String hotelId = getHotelId();
        long version = versionService.getKitchenVersion(hotelId);
        long count = versionService.getKitchenOrderCount(hotelId, tableNumber);

        return ResponseEntity.ok(Map.of(
                "version", version,             // timestamp millis
                "activeOrderCount", count,      // active orders for this table
                "tableNumber", tableNumber
        ));
    }

    // ─────────────────────────────────────────────────────
    // KITCHEN API 2: Delta since timestamp for table
    // GET /api/v1/sync/kitchen/delta?tableNumber=3&since=1746057600000
    // ─────────────────────────────────────────────────────
    @GetMapping("/kitchen/delta")
    public ResponseEntity<Map<String, Object>> getKitchenDelta(
            @RequestParam int tableNumber,
            @RequestParam long since) {

        String hotelId = getHotelId();

        LocalDateTime sinceTime = Instant.ofEpochMilli(since)
                .atZone(ZoneId.of("Asia/Kolkata"))
                .toLocalDateTime();

        List<?> deltaOrders = versionService.getKitchenDeltaSince(
                hotelId, tableNumber, sinceTime);
        long activeCount = versionService.getKitchenOrderCount(
                hotelId, tableNumber);

        return ResponseEntity.ok(Map.of(
                "deltaOrders", deltaOrders,     // only changed orders for table
                "activeOrderCount", activeCount,
                "tableNumber", tableNumber
        ));
    }
}