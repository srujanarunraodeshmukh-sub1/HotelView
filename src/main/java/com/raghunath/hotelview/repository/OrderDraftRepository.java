package com.raghunath.hotelview.repository;

import com.raghunath.hotelview.entity.OrderDraft;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface OrderDraftRepository extends MongoRepository<OrderDraft, String> {
    Optional<OrderDraft> findByHotelIdAndTableName(String hotelId, String tableName);
    void deleteByHotelIdAndTableName(String hotelId, String tableName);
}
