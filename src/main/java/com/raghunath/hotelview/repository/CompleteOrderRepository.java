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

    // Sorted across multiple dates chronologically using combined fields
    List<CompletedOrder> findTop10ByHotelIdOrderByCheckoutDateDescCheckoutTimeDesc(String hotelId);

    List<CompletedOrder> findByHotelIdAndCheckoutDateBetween(String hotelId, String startDate, String endDate);

    // Sorted for specific order types and single days cleanly
    List<CompletedOrder> findByHotelIdAndOrderTypeAndCheckoutDateOrderByCheckoutDateDescCheckoutTimeDesc(
            String hotelId, String orderType, String checkoutDate);

    Long countByHotelIdAndCheckoutDate(String hotelId, String checkoutDate);

    // MongoDB Aggregation for Summing Grand Totals
    @Aggregation(pipeline = {
            "{ $match: { hotelId: ?0, checkoutDate: ?1 } }",
            "{ $group: { _id: null, total: { $sum: '$grandTotal' } } }"
    })
    Double sumGrandTotalByHotelIdAndCheckoutDate(String hotelId, String checkoutDate);

    // --- Paging API (Only fetches ID, Name, Mobile, Date, Time, Total and Type) ---
    @Query(value = "{ 'hotelId' : ?0 }",
            fields = "{ 'id':1, 'customerName':1, 'customerMobile':1, 'checkoutDate':1, 'checkoutTime':1, 'totalPayable':1, 'orderType':1 }")
    Page<CompletedOrder> findByHotelId(String hotelId, Pageable pageable);

    // --- Search API (Search by Name OR Mobile) ---
    @Query(value = "{ 'hotelId': ?0, $or: [ { 'customerName': { $regex: ?1, $options: 'i' } }, { 'customerMobile': { $regex: ?1 } } ] }")
    List<CompletedOrder> searchOrders(String hotelId, String searchString);

    // ✅ FIXED: Replaced non-existent lastModified query properties with valid string timelines
    List<CompletedOrder> findByHotelIdAndCheckoutDateGreaterThanEqualOrderByCheckoutDateDescCheckoutTimeDesc(
            String hotelId, String sinceDate);

    // For today's full data
    List<CompletedOrder> findByHotelIdAndCheckoutDateOrderByCheckoutDateDescCheckoutTimeDesc(
            String hotelId, String checkoutDate);

    Long countByHotelIdAndOrderTypeAndCheckoutDate(String hotelId, String orderType, String checkoutDate);

    // MongoDB Aggregation for Summing Net Total Payable Amounts
    @Aggregation(pipeline = {
            "{ '$match': { 'hotelId' : ?0, 'checkoutDate' : ?1 } }",
            "{ '$group': { '_id': null, 'total': { '$sum': '$totalPayable' } } }"
    })
    Double sumTotalPayableByHotelIdAndCheckoutDate(String hotelId, String checkoutDate);

    // Separated properties cleanly with 'And' operator keywords for list criteria filters
    List<CompletedOrder> findByHotelIdAndOrderTypeInAndCheckoutDateOrderByCheckoutDateDescCheckoutTimeDesc(
            String hotelId, List<String> orderTypes, String checkoutDate);

    long countByHotelId(String hotelId);

    Long countByHotelIdAndOrderTypeInAndCheckoutDate(String hotelId, List<String> deliveryTypes, String todayDate);
}