package com.raghunath.hotelview.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class CheckoutRequest {
    private List<String> orderIds;
    private String customerName;
    private String customerMobile;
    private String customerAddress;
}
