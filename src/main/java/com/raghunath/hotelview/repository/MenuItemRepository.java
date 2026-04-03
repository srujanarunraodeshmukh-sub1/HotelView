package com.raghunath.hotelview.repository;

import com.raghunath.hotelview.entity.MenuItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MenuItemRepository extends MongoRepository<MenuItem,String> {

    List<MenuItem> findByHotelIdAndIsApprovedTrue(String hotelId);

    List<MenuItem> findByHotelIdAndCategoryAndIsApprovedTrue(String hotelId,String category);

    Optional<MenuItem> findByHotelIdAndId(String hotelId,String id);

    Optional<MenuItem> findByHotelIdAndName(String hotelId, String name);

    Page<MenuItem> findByHotelIdAndIsApprovedTrue(String hotelId, Pageable pageable);
    Long countByHotelId(String hotelId);

    // In MenuItemRepository.java
    @Query("{ 'hotelId': ?0, 'name': { $regex: '^?1', $options: 'i' }, 'isApproved': true }")
    List<MenuItem> findByHotelIdAndNameStartingWithIgnoreCase(String hotelId, String name);
}