package com.raghunath.hotelview.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Document(collection= "tables")
public class RestaurantTable {
    @Id
    private String id;
    private String hotelId;
    private int tableNumber;
    private int capacity;
    private String status;
    private Double currentBill;

}
