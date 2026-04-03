package com.raghunath.hotelview.repository;

import com.raghunath.hotelview.entity.KitchenOrder;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface KitchenOrderRepository extends MongoRepository<KitchenOrder, String> {

    List<KitchenOrder> findByHotelIdAndStatusIn(String hotelId, List<String> statuses);

    // Changed 'int' to 'Integer' to support Home Delivery (nulls)
    List<KitchenOrder> findByHotelIdAndTableNumberAndStatusNotOrderByCreatedAtDesc(
            String hotelId, Integer tableNumber, String status);

    List<KitchenOrder> findByHotelIdAndStatus(String hotelId, String status);

    Long countByHotelIdAndOrderTypeAndStatus(String hotelId, String orderType, String status);
}