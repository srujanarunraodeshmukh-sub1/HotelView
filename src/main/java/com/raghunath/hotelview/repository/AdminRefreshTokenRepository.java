package com.raghunath.hotelview.repository;

import com.raghunath.hotelview.entity.AdminRefreshToken;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface AdminRefreshTokenRepository extends MongoRepository<AdminRefreshToken, String> {

    Optional<AdminRefreshToken> findByToken(String token);

    List<AdminRefreshToken> findByUserId(String userId);

    // Fast check for the Filter
    boolean existsByUserId(String userId);

    long countByUserId(String userId);

    void deleteByUserId(String userId);

    long deleteByToken(String token);
}