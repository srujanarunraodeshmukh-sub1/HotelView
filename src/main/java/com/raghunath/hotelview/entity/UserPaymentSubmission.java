package com.raghunath.hotelview.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "user_payment_submissions") // Separate from system payment info
public class UserPaymentSubmission {
    @Id
    private String id;
    private String hotelId;
    private String name;
    private String address;
    private String screenshotUrl;
    private String submissionDate; // e.g., 2026-05-10
    private String submissionTime; // e.g., 16:15:00
    private LocalDateTime createdAt;
    private String status; // Default: "PENDING"

    public void save(UserPaymentSubmission submission) {
    }
}