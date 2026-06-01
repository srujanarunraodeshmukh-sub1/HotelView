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
@CompoundIndex(name = "hotel_table_status", def = "{'hotelId': 1, 'tableName': 1, 'status': 1}")
@Document(collection = "kitchen_orders")
public class KitchenOrder {
    @Id
    private String id;

    @Field("hotelId")
    private String hotelId;

    @Field("tableName")
    private String tableName;

    @Field("orderType")
    private String orderType;

    private List<OrderItem> items;
    private Double totalAmount;
    private String status;
    private String comments;

    private String placedBy;
    private String editedBy;
    private String acceptedBy;
    private String completedBy;

    private String createdDate;
    private String createdTime;

    @Field("updatedAt")
    private LocalDateTime updatedAt;
}
