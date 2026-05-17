package com.raghunath.hotelview.repository;

import com.raghunath.hotelview.entity.RestaurantTable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TableRepository extends MongoRepository<RestaurantTable, String> {

    List<RestaurantTable> findAllByHotelIdOrderByTableNameAsc(String hotelId);

    Optional<RestaurantTable> findByHotelIdAndTableName(String hotelId, String tableName);

    boolean existsByHotelId(String hotelId);

    boolean existsByHotelIdAndTableName(String hotelId, String tableName);

    Long countByHotelIdAndStatus(String hotelId, String status);
    // Uses 'Not' to exclude a specific status
    Long countByHotelIdAndStatusNot(String hotelId, String status);

    Long countByHotelIdAndStatusIn(String hotelId, List<String> statuses);

    long countByHotelId(String hotelId);

    List<RestaurantTable> findByHotelIdAndUpdatedAtAfter(
            String hotelId, LocalDateTime updatedAt);
}
