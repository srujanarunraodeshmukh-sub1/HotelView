package com.raghunath.hotelview.repository;

import com.raghunath.hotelview.entity.Plan; // Make sure this matches your Plan entity package
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlanRepository extends MongoRepository<Plan, String> {
    // You can add custom query methods here later if needed
}