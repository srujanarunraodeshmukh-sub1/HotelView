package com.raghunath.hotelview.repository;

import com.raghunath.hotelview.entity.CompletedOrder;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CompleteOrderRepository extends MongoRepository<CompletedOrder, String> {
    List<CompletedOrder> findByHotelIdAndCheckoutDate(String hotelId, String checkoutDate);

    List<CompletedOrder> findByHotelIdAndCustomerMobile(String hotelId, String customerMobile);

    List<CompletedOrder> findTop10ByHotelIdOrderByCheckoutAtDesc(String hotelId);

    List<CompletedOrder> findByHotelIdAndCheckoutDateBetween(String hotelId, String startDate, String endDate);
}
