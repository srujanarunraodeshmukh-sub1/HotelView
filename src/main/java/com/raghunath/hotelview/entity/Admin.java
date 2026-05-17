package com.raghunath.hotelview.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "admins")
public class Admin {

    @Id
    private String id;

    private String name;

    @Indexed(unique = true)
    private String mobile;

    private String alternateMobile;

    private String password;

    private String emailId;

    private String address;

    private String hotelId;

    private boolean isActive;

    private boolean isApproved;

    private int maxLogins = 1;

    private String planType;

    private LocalDateTime subscriptionStart;
    private LocalDateTime subscriptionExpiry;

    private String restaurantName;
    private String restaurantAddress;
    private String restaurantContact;
    private String restaurantLogo;
    private String restaurantUpi;
    private String personalMessage;

    private Map<String, String> platformIds = new HashMap<>();

    private Map<String, Boolean> integrationStatus = new HashMap<>();
}