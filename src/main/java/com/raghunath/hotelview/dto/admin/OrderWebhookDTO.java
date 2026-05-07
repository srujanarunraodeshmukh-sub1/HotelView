package com.raghunath.hotelview.dto.admin;

import lombok.*;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderWebhookDTO {
    private String hotelId;
    private String merchantId;
    private String platformName;
    private String externalOrderId;
    private String customerName;
    private String customerContact;
    private List<ExternalOrderItemDTO> items; // 👈 Updated to use the new DTO
    private double totalAmount;
    private String orderStatus;
    private String deliveryAddress;
}