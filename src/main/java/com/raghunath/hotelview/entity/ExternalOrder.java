package com.raghunath.hotelview.entity;

import com.raghunath.hotelview.dto.admin.OrderItem;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Document(collection = "external_orders")
public class ExternalOrder {
    @Id
    private String id;
    private String hotelId;

    // Platform Info
    private String platform; // ZOMATO, SWIGGY
    private String externalOrderId;

    // Customer Info (The data you need for delivery)
    private String customerName;
    private String customerMobile;
    private String deliveryAddress;

    // Order Content
    private List<OrderItem> items;
    private Double totalAmount;

    // Flow Status
    private String status; // RECEIVED, ACCEPTED, REJECTED
    private LocalDateTime receivedAt;
}