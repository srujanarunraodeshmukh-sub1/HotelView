package com.raghunath.hotelview.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Document(collection= "tables")
public class RestaurantTable {
    @Id
    private String id;
    private String hotelId;

    private Integer tableNumber; // 👈 Change from 'int' to 'Integer'

    @org.springframework.data.mongodb.core.mapping.Field("seatingCapacity")
    private Integer seatingCapacity;
    private String status;
    private Double currentBill;
    @Field("updatedAt")
    private LocalDateTime updatedAt;
}