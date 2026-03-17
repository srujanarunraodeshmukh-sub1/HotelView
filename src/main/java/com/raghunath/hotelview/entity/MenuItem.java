package com.raghunath.hotelview.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "menu_items")
public class MenuItem {

    @Id
    private String id;

    @Indexed
    private String hotelId;

    @Indexed
    private String category;

    private String name;

    private String description;

    private BigDecimal price;

    private Boolean isVeg = true;

    private Boolean isAvailable = true;

    private String imageUrl;

    private Integer preparationTime;

    private Boolean isApproved = false; // controlled by Madhava Global

    private LocalDateTime createdAt;

}