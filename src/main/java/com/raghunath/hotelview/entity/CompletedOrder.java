package com.raghunath.hotelview.entity;

import com.fasterxml.jackson.annotation.JsonInclude; // 👈 Add this import
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.annotation.LastModifiedDate;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Document(collection = "completed_orders")
@JsonInclude(JsonInclude.Include.NON_NULL) // 👈 Add this to hide null fields from JSON
public class CompletedOrder {
    @Id
    private String id;
    private String hotelId;
    private String orderType; // 👈 This will now show up in the list

    private String customerName;
    private String customerMobile;
    private String customerAddress;

    private List<KitchenOrder> allOrders;

    private Double grandTotal;
    private String paymentStatus;
    private Double discountPercent;

    private Double discountAmount;
    private LocalDateTime lastModified;

    private Double totalPayable;

    private LocalDateTime checkoutAt;
    private String checkoutDate;
    private String checkoutTime;
}