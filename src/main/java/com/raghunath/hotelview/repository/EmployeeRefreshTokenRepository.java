package com.raghunath.hotelview.repository;

import com.raghunath.hotelview.entity.EmployeeRefreshToken;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface EmployeeRefreshTokenRepository extends MongoRepository<EmployeeRefreshToken, String> {
    Optional<EmployeeRefreshToken> findByToken(String token);
    void deleteByUserId(String userId);
    // Add this to both Repository interfaces
    long countByUserId(String userId);

    long deleteByToken(String token);
}