package com.raghunath.hotelview.repository;

import com.raghunath.hotelview.entity.MenuItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MenuItemRepository extends MongoRepository<MenuItem, String> {

    List<MenuItem> findByHotelIdAndIsApprovedTrue(String hotelId);

    List<MenuItem> findByHotelIdAndCategoryAndIsApprovedTrue(String hotelId, String category);

    Optional<MenuItem> findByHotelIdAndId(String hotelId, String id);

    Optional<MenuItem> findByHotelIdAndName(String hotelId, String name);

    Page<MenuItem> findByHotelIdAndIsApprovedTrue(String hotelId, Pageable pageable);

    Long countByHotelId(String hotelId);

    /**
     * ADVANCED SEARCH LOGIC:
     * 1. Matches ShortCode starting with query (Highest Priority)
     * 2. Matches Name starting with query (Medium Priority)
     * 3. Matches Name containing query anywhere (Lowest Priority)
     */
    @Query("{ 'hotelId': ?0, 'isApproved': true, '$or': [ " +
            "{ 'shortCode': { '$regex': '^?1', '$options': 'i' } }, " +
            "{ 'name': { '$regex': '^?1', '$options': 'i' } }, " +
            "{ 'name': { '$regex': ?1, '$options': 'i' } } " +
            "] }")
    List<MenuItem> findByHotelIdAndSearchQuery(String hotelId, String query);
}