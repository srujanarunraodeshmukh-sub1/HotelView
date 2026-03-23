package com.raghunath.hotelview.repository;

import com.raghunath.hotelview.entity.AdminRefreshToken;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface AdminRefreshTokenRepository extends MongoRepository<AdminRefreshToken, String> {
    Optional<AdminRefreshToken> findByToken(String token);
    void deleteByUserId(String userId);
    // Add this to both Repository interfaces
    long countByUserId(String userId);

    long deleteByToken(String token);
}