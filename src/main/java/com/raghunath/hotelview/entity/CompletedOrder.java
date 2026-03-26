package com.raghunath.hotelview.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Document(collection = "completed_orders")
public class CompletedOrder {
    @Id
    private String id;
    private String hotelId;
    private String orderType;

    private String customerName;
    private String customerMobile;
    private String customerAddress;

    private List<KitchenOrder> allOrders;

    private Double grandTotal;
    private String paymentStatus;

    private LocalDateTime checkoutAt;
    private String checkoutDate;
    private String checkoutTime;
}
