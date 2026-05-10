package com.raghunath.hotelview.repository;

import com.raghunath.hotelview.entity.UserPaymentSubmission;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserPaymentSubmissionRepository extends MongoRepository<UserPaymentSubmission, String> {
}