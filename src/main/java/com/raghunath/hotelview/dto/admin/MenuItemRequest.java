package com.raghunath.hotelview.dto.admin;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
public class MenuItemRequest {

    private String hotelId;

    private String category;

    private String name;

    private String shortCode;

    private String description;

    private BigDecimal price;

    private Boolean isVeg;

    private Boolean isAvailable;

    private String imageUrl;

    private Integer preparationTime;

}