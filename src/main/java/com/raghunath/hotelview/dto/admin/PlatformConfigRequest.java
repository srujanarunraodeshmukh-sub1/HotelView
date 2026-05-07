package com.raghunath.hotelview.dto.admin;

import lombok.Data;

@Data
public class PlatformConfigRequest {
    private String platformName; // e.g., "ZOMATO"
    private String merchantId;   // e.g., "dewrthaqe3rt45erg"
}