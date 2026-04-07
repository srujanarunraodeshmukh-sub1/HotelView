package com.raghunath.hotelview.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
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
// PRODUCTION INDEX: Ensures hotelId + category lookups are instant
@CompoundIndex(name = "hotel_category_idx", def = "{'hotelId': 1, 'category': 1}")
public class MenuItem {

    @Id
    private String id;

    @Indexed
    private String hotelId;

    private String category;

    private String name;

    private String shortCode;

    private String description;

    private BigDecimal price;

    private Boolean isVeg = true;

    private Boolean isAvailable = true;

    private String imageUrl;

    private Integer preparationTime;

    private Boolean isApproved = false;

    private LocalDateTime createdAt;
}