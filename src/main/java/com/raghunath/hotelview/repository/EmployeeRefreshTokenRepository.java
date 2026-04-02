package com.raghunath.hotelview.repository;

import com.raghunath.hotelview.entity.EmployeeRefreshToken;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeRefreshTokenRepository extends MongoRepository<EmployeeRefreshToken, String> {

    Optional<EmployeeRefreshToken> findByToken(String token);

    // To support multi-device logic in the Filter
    List<EmployeeRefreshToken> findByUserId(String userId);

    // Fast check for security filter
    boolean existsByUserId(String userId);

    long countByUserId(String userId);

    void deleteByUserId(String userId);

    long deleteByToken(String token);
}