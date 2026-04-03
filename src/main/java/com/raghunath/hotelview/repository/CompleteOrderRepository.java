package com.raghunath.hotelview.repository;

import com.raghunath.hotelview.entity.CompletedOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import java.util.List;

public interface CompleteOrderRepository extends MongoRepository<CompletedOrder, String> {

    // Existing methods kept for your Sales Graphs
    List<CompletedOrder> findByHotelIdAndCheckoutDate(String hotelId, String checkoutDate);
    List<CompletedOrder> findByHotelIdAndCustomerMobile(String hotelId, String customerMobile);
    List<CompletedOrder> findTop10ByHotelIdOrderByCheckoutAtDesc(String hotelId);
    List<CompletedOrder> findByHotelIdAndCheckoutDateBetween(String hotelId, String startDate, String endDate);
    List<CompletedOrder> findByHotelIdAndOrderTypeAndCheckoutDateOrderByCheckoutAtDesc(
            String hotelId, String orderType, String checkoutDate);

    Long countByHotelIdAndCheckoutDate(String hotelId, String checkoutDate);

    // MongoDB Aggregation for Sum
    @Aggregation("{ $match: { hotelId: ?0, checkoutDate: ?1 } }, { $group: { _id: null, total: { $sum: '$grandTotal' } } }")
    Double sumGrandTotalByHotelIdAndCheckoutDate(String hotelId, String checkoutDate);

    // --- NEW: Paging API (Only fetches ID, Name, Mobile, Date, and Total) ---
    // In CompleteOrderRepository.java

    @Query(value = "{ 'hotelId' : ?0 }",
            fields = "{ 'id':1, 'customerName':1, 'customerMobile':1, 'checkoutAt':1, 'grandTotal':1, 'orderType':1 }")
    Page<CompletedOrder> findByHotelId(String hotelId, Pageable pageable);

    // --- NEW: Search API (Search by Name OR Mobile) ---
    @Query(value = "{ 'hotelId': ?0, $or: [ { 'customerName': { $regex: ?1, $options: 'i' } }, { 'customerMobile': { $regex: ?1 } } ] }")
    List<CompletedOrder> searchOrders(String hotelId, String searchString);
}