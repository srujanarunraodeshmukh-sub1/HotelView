package com.raghunath.hotelview.dto.admin;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class MenuItemResponse {

    private String id;

    private String name;

    private String category;

    private BigDecimal price;

    private Boolean isVeg;

    private Boolean isAvailable;

    private String imageUrl;

}