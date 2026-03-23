package com.raghunath.hotelview.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "employee_refresh_tokens") // or "employee_refresh_tokens"
public class EmployeeRefreshToken {
    @Id
    private String id;
    @Indexed
    private String userId;   // Admin ID or Employee ID
    @Indexed(unique = true)
    private String token;    // The actual JWT
    private LocalDateTime expiryDate;
}