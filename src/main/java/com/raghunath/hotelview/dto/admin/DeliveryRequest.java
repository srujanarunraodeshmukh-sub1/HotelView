package com.raghunath.hotelview.dto.admin;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class DeliveryRequest {
    @NotNull
    private List<OrderItem> items;
    private String orderType; // e.g., "HOME_DELIVERY", "TAKEAWAY"
    private String customerNote;
}