package com.raghunath.hotelview.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Document(collection = "admin_refresh_tokens")
public class AdminRefreshToken {
    @Id
    private String id;
    private String userId;
    private String token;
    private LocalDateTime expiryDate;

}