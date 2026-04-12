package com.raghunath.hotelview.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

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

    private String password;

    private String hotelId;

    private boolean approved;

    private boolean active;

    private int maxLogins = 1;

    private String restaurantName;
    private String restaurantAddress;
    private String restaurantContact;
    private String restaurantLogo;
    private String restaurantUpi;

}