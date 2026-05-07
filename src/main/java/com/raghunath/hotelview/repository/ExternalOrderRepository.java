package com.raghunath.hotelview.repository;

import com.raghunath.hotelview.entity.ExternalOrder;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface ExternalOrderRepository extends MongoRepository<ExternalOrder, String> {
    List<ExternalOrder> findByHotelIdAndStatus(String hotelId, String status);
}