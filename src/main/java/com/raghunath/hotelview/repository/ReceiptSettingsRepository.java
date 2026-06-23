package com.raghunath.hotelview.repository;

import com.raghunath.hotelview.entity.ReceiptSettings;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReceiptSettingsRepository extends MongoRepository<ReceiptSettings, String> {
    Optional<ReceiptSettings> findByHotelIdAndReceiptType(String hotelId, String receiptType);
}