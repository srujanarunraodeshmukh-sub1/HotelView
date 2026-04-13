package com.raghunath.hotelview.service.admin;

import com.raghunath.hotelview.entity.KitchenOrder;
import com.raghunath.hotelview.entity.RestaurantTable;
import com.raghunath.hotelview.entity.SystemVersion;
import com.raghunath.hotelview.repository.KitchenOrderRepository;
import com.raghunath.hotelview.repository.TableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VersionService {

    private final MongoTemplate mongoTemplate;
    private final TableRepository tableRepository;
    private final KitchenOrderRepository kitchenOrderRepository;

    // ── Bump Methods ──────────────────────────────────────
    public void bumpSales(String hotelId) {
        update(hotelId, "salesVersion");
    }

    public void bumpTables(String hotelId) {
        update(hotelId, "tableVersion");
    }

    public void bumpMenu(String hotelId) {
        update(hotelId, "menuVersion");
    }

    public void bumpEmployee(String hotelId) {
        update(hotelId, "employeeVersion");
    }

    public void bumpKitchen(String hotelId) {
        update(hotelId, "kitchenVersion");
    }

    private void update(String hotelId, String field) {
        LocalDateTime now = LocalDateTime.now();
        mongoTemplate.upsert(
                new Query(Criteria.where("hotelId").is(hotelId)),
                new Update()
                        .set(field, toEpochMilli(now)) // ✅ Store as timestamp
                        .set("lastUpdated", now),
                SystemVersion.class
        );
    }

    // ── Version as Timestamp (milliseconds) ──────────────
    public long getSalesVersion(String hotelId) {
        return getTimestamp(hotelId, "salesVersion");
    }

    public long getTableVersion(String hotelId) {
        return getTimestamp(hotelId, "tableVersion");
    }

    public long getMenuVersion(String hotelId) {
        return getTimestamp(hotelId, "menuVersion");
    }

    public long getEmployeeVersion(String hotelId) {
        return getTimestamp(hotelId, "employeeVersion");
    }

    public long getKitchenVersion(String hotelId) {
        return getTimestamp(hotelId, "kitchenVersion");
    }

    private long getTimestamp(String hotelId, String field) {
        SystemVersion sv = mongoTemplate.findOne(
                new Query(Criteria.where("hotelId").is(hotelId)),
                SystemVersion.class);
        if (sv == null) return 0L;
        return switch (field) {
            case "salesVersion" ->
                    sv.getSalesVersion() != null ? sv.getSalesVersion() : 0L;
            case "tableVersion" ->
                    sv.getTableVersion() != null ? sv.getTableVersion() : 0L;
            case "menuVersion" ->
                    sv.getMenuVersion() != null ? sv.getMenuVersion() : 0L;
            case "employeeVersion" ->
                    sv.getEmployeeVersion() != null ? sv.getEmployeeVersion() : 0L;
            case "kitchenVersion" ->
                    sv.getKitchenVersion() != null ? sv.getKitchenVersion() : 0L;
            default -> 0L;
        };
    }

    // ── Count Helpers ─────────────────────────────────────
    public long getTableCount(String hotelId) {
        return tableRepository.countByHotelId(hotelId);
    }

    public long getKitchenOrderCount(String hotelId, int tableNumber) {
        return kitchenOrderRepository
                .countByHotelIdAndTableNumberAndStatusNot(
                        hotelId, tableNumber, "PAID");
    }

    // ── Delta Helpers ─────────────────────────────────────
    public List<RestaurantTable> getTablesDeltaSince(
            String hotelId, LocalDateTime since) {
        return tableRepository.findByHotelIdAndUpdatedAtAfter(hotelId, since);
    }

    public List<KitchenOrder> getKitchenDeltaSince(
            String hotelId, int tableNumber, LocalDateTime since) {
        return kitchenOrderRepository
                .findByHotelIdAndTableNumberAndUpdatedAtAfterOrderByCreatedAtDesc(
                        hotelId, tableNumber, since);
    }

    // ── Helper ────────────────────────────────────────────
    private long toEpochMilli(LocalDateTime ldt) {
        return ldt.atZone(ZoneId.of("Asia/Kolkata"))
                .toInstant()
                .toEpochMilli();
    }
}