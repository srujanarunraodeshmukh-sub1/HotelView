package com.raghunath.hotelview.entity;

import com.raghunath.hotelview.dto.admin.OrderItem;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@CompoundIndex(name = "hotel_table_status", def = "{'hotelId': 1, 'tableNumber': 1, 'status': 1}")
@Document(collection = "kitchen_orders")
public class KitchenOrder {
    @Id
    private String id;

    @org.springframework.data.mongodb.core.mapping.Field("hotelId")
    private String hotelId;

    @org.springframework.data.mongodb.core.mapping.Field("tableNumber")
    private Integer tableNumber; // Integer allows NULL for Home Delivery

    @org.springframework.data.mongodb.core.mapping.Field("orderType")
    private String orderType;

    private List<OrderItem> items;
    private Double totalAmount;
    private String status;
    private String comments;

    private String createdBy;
    private String acceptedBy;

    private LocalDateTime createdAt;
    private String createdDate;
    private String createdTime;
    @Field("updatedAt")
    private LocalDateTime updatedAt;
}