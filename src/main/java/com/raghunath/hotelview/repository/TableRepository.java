package com.raghunath.hotelview.repository;

import com.raghunath.hotelview.entity.RestaurantTable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TableRepository extends MongoRepository<RestaurantTable, String> {

    List<RestaurantTable> findAllByHotelIdOrderByTableNumberAsc(String hotelId);

    Optional<RestaurantTable> findByHotelIdAndTableNumber(String hotelId, int tableNumber);

    boolean existsByHotelId(String hotelId);

    boolean existsByHotelIdAndTableNumber(String hotelId, int tableNumber);

    Long countByHotelIdAndStatus(String hotelId, String status);
    // Uses 'Not' to exclude a specific status
    Long countByHotelIdAndStatusNot(String hotelId, String status);

    Long countByHotelIdAndStatusIn(String hotelId, List<String> statuses);

    long countByHotelId(String hotelId);

    List<RestaurantTable> findByHotelIdAndUpdatedAtAfter(
            String hotelId, LocalDateTime updatedAt);
}
