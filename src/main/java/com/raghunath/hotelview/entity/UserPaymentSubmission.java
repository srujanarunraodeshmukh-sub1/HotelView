package com.raghunath.hotelview.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "user_payment_submissions")
public class UserPaymentSubmission {
    @Id
    private String id;
    private String hotelId;
    private String name;
    private String address;
    private String screenshotUrl;
    private String submissionDate;
    private String submissionTime;
    private LocalDateTime createdAt;
    private String status;
    // REMOVED the wrong save() method
}