package com.raghunath.hotelview.service.admin;

import com.raghunath.hotelview.dto.admin.DeliverySummaryDTO;
import com.raghunath.hotelview.dto.admin.ReceiptResponse;
import com.raghunath.hotelview.entity.Admin;
import com.raghunath.hotelview.entity.CompletedOrder;
import com.raghunath.hotelview.entity.KitchenOrder;
import com.raghunath.hotelview.repository.AdminRepository;
import com.raghunath.hotelview.repository.CompleteOrderRepository;
import com.raghunath.hotelview.repository.KitchenOrderRepository;
import com.raghunath.hotelview.repository.TableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompletedOrderService {

    private final CompleteOrderRepository completeOrderRepository;
    private final AdminRepository adminRepository;
    private final KitchenOrderRepository kitchenOrderRepository;
    private final TableRepository tableRepository;
    private final VersionService versionService;

    @Autowired
    private MongoTemplate mongoTemplate;

    // API 1: Paged Fetch
    public Page<DeliverySummaryDTO> getCompletedOrdersPaged(String hotelId, int pageNumber) {
        Pageable pageable = PageRequest.of(pageNumber, 10, Sort.by("checkoutAt").descending());

        // 1. Fetch the Entity Page
        Page<CompletedOrder> entities = completeOrderRepository.findByHotelId(hotelId, pageable);

        // 2. Map Entities to DeliverySummaryDTO
        return entities.map(order -> DeliverySummaryDTO.builder()
                .id(order.getId())
                .orderType(order.getOrderType())
                .customerName(order.getCustomerName())
                .customerMobile(order.getCustomerMobile())
                .totalPayable(order.getTotalPayable())
                .checkoutAt(order.getCheckoutAt())
                .build());
    }

    public ReceiptResponse getReceiptDetails(String orderId, String hotelIdFromToken) {
        // 1. Fetch the Completed Order
        CompletedOrder order = completeOrderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        // Security Check: Ensure this order belongs to the hotel in the token
        if (!order.getHotelId().equals(hotelIdFromToken)) {
            throw new RuntimeException("Unauthorized access to this order");
        }

        // 2. Fetch Restaurant Details from Admin Entity
        Admin admin = (Admin) adminRepository.findByHotelId(hotelIdFromToken)
                .orElseThrow(() -> new RuntimeException("Restaurant profile not found"));

        // 3. Flatten all nested items from allOrders array into one list for the receipt
        List<ReceiptResponse.FlattenedItem> flattenedItems = order.getAllOrders().stream()
                .flatMap(kitchenOrder -> kitchenOrder.getItems().stream())
                .map(item -> ReceiptResponse.FlattenedItem.builder()
                        .itemName(item.getItemName())
                        .quantity(item.getQuantity())
                        .price(item.getPrice())
                        .subTotal(item.getSubTotal())
                        .build())
                .collect(Collectors.toList());

        // 4. Build the final Print-Ready response
        return ReceiptResponse.builder()
                .restaurantName(admin.getRestaurantName())
                .restaurantAddress(admin.getRestaurantAddress())
                .restaurantContact(admin.getRestaurantContact())
                .restaurantLogo(admin.getRestaurantLogo())
                .restaurantUpi(admin.getRestaurantUpi())
                .orderId(order.getId())
                .date(order.getCheckoutDate())
                .time(order.getCheckoutTime())
                .orderType(order.getOrderType())
                .items(flattenedItems)

                // --- NEW PRICING MAPPING ---
                .grandTotal(order.getGrandTotal())
                .discountPercent(order.getDiscountPercent())
                .discountAmount(order.getDiscountAmount())
                .totalPayable(order.getTotalPayable())
                // ---------------------------

                .customerName(order.getCustomerName())
                .customerMobile(order.getCustomerMobile())
                .customerAddress(order.getCustomerAddress())
                .build();
    }

    public List<CompletedOrder> searchCompletedOrders(String hotelId, String query) {
        return completeOrderRepository.searchOrders(hotelId, query);
    }

    /**
     * FETCH TODAY'S COMPLETED HOME DELIVERIES (Clean Summary):
     * Returns only the essential fields for the dashboard list.
     */
    public List<DeliverySummaryDTO> getTodayCompletedHomeDeliveries(String hotelId) {
        ZonedDateTime nowIST = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
        String todayDate = nowIST.format(DateTimeFormatter.ISO_LOCAL_DATE);

        // 🚀 Update: Define the list of types
        List<String> externalTypes = List.of("HOME", "PARCEL");

        // 🚀 Update: Use the In query with the list
        List<CompletedOrder> orders = completeOrderRepository
                .findByHotelIdAndOrderTypeInAndCheckoutDateOrderByCheckoutAtDesc(
                        hotelId, externalTypes, todayDate);

        // Map Entity to DTO (Kept exactly as you provided)
        return orders.stream().map(order -> DeliverySummaryDTO.builder()
                        .id(order.getId())
                        .orderType(order.getOrderType())
                        .customerName(order.getCustomerName())
                        .customerMobile(order.getCustomerMobile())
                        .totalPayable(order.getTotalPayable())
                        .checkoutAt(order.getCheckoutAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public void softDeleteOrders(String hotelId, List<String> orderIds) {
        // 1. Fetch orders from both sources
        List<CompletedOrder> completedOrders = completeOrderRepository.findAllById(orderIds)
                .stream().filter(o -> o.getHotelId().equals(hotelId)).toList();

        List<KitchenOrder> kitchenOrders = kitchenOrderRepository.findAllById(orderIds)
                .stream().filter(o -> o.getHotelId().equals(hotelId)).toList();

        if (completedOrders.isEmpty() && kitchenOrders.isEmpty()) {
            throw new RuntimeException("Unauthorized or Orders not found");
        }

        // 🚀 2. Calculate deduction and identify tables involved
        double totalDeduction = 0.0;
        Set<String> tablesToUpdate = new HashSet<>();

        // FIX: Get tableNumber from the nested list in CompletedOrder
        for (CompletedOrder o : completedOrders) {
            totalDeduction += (o.getTotalPayable() != null) ? o.getTotalPayable() : 0.0;

            if (o.getAllOrders() != null && !o.getAllOrders().isEmpty()) {
                String tNum = o.getAllOrders().get(0).getTableName();
                if (tNum != null) tablesToUpdate.add(tNum);
            }
        }

        // Process Kitchen Orders normally
        for (KitchenOrder o : kitchenOrders) {
            totalDeduction += (o.getTotalAmount() != null) ? o.getTotalAmount() : 0.0;
            if (o.getTableName() != null) {
                tablesToUpdate.add(o.getTableName());
            }
        }

        // 3. Update the RestaurantTable currentBill
        if (!tablesToUpdate.isEmpty()) {
            for (String tableName : tablesToUpdate) {
                double finalTotalDeduction = totalDeduction;
                tableRepository.findByHotelIdAndTableName(hotelId, tableName).ifPresent(table -> {
                    double current = (table.getCurrentBill() != null) ? table.getCurrentBill() : 0.0;
                    double newBill = Math.max(0, current - finalTotalDeduction);

                    table.setCurrentBill(newBill);

                    // If bill becomes 0, set status to AVAILABLE
                    if (newBill <= 0) {
                        table.setStatus("INACTIVE");
                    }

                    tableRepository.save(table);
                });
            }
        }

        // 4. Move to Trash with deletedAt IST
        ZonedDateTime nowIST = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
        String deletedAt = nowIST.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        List<org.bson.Document> trashDocs = new ArrayList<>();

        // Convert and add deletedAt to documents
        completedOrders.forEach(order -> {
            org.bson.Document doc = new org.bson.Document();
            mongoTemplate.getConverter().write(order, doc);
            doc.put("deletedAt", deletedAt);
            trashDocs.add(doc);
        });

        kitchenOrders.forEach(order -> {
            org.bson.Document doc = new org.bson.Document();
            mongoTemplate.getConverter().write(order, doc);
            doc.put("deletedAt", deletedAt);
            trashDocs.add(doc);
        });

        // 5. Final DB Operations
        if (!trashDocs.isEmpty()) {
            mongoTemplate.insert(trashDocs, "deleted_orders");
            if (!completedOrders.isEmpty()) completeOrderRepository.deleteAll(completedOrders);
            if (!kitchenOrders.isEmpty()) kitchenOrderRepository.deleteAll(kitchenOrders);
        }

        // 6. Sync Versions
        versionService.bumpSales(hotelId);
        versionService.bumpTables(hotelId);
    }

    public List<org.bson.Document> getDeletedOrders(String hotelId) {
        // 1. Query for the specific hotel
        Query query = new Query(Criteria.where("hotelId").is(hotelId));

        // 2. Fetch as raw Documents from the 'deleted_orders' collection
        // This bypasses Java class restrictions and gets EVERY field (items, amount, etc.)
        List<org.bson.Document> rawDocs = mongoTemplate.find(query, org.bson.Document.class, "deleted_orders");

        // 3. Clean up the response for the frontend
        return rawDocs.stream().map(doc -> {
            // Convert ObjectId to String for the 'id' field
            if (doc.containsKey("_id")) {
                doc.put("id", doc.get("_id").toString());
                doc.remove("_id");
            }
            // Remove the internal Java class metadata so the JSON is clean
            doc.remove("_class");
            return doc;
        }).collect(java.util.stream.Collectors.toList());
    }


}
