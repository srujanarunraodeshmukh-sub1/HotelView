package com.raghunath.hotelview.repository;

import com.raghunath.hotelview.entity.RestaurantTable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface TableRepository extends MongoRepository<RestaurantTable, String> {

    List<RestaurantTable> findAllByHotelIdOrderByTableNumberAsc(String hotelId);

    Optional<RestaurantTable> findByHotelIdAndTableNumber(String hotelId, int tableNumber);

    boolean existsByHotelId(String hotelId);

    boolean existsByHotelIdAndTableNumber(String hotelId, int tableNumber);

    Long countByHotelIdAndStatus(String hotelId, String status);
}
