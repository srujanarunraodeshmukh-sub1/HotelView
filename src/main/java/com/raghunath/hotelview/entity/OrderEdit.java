package com.raghunath.hotelview.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "order_edits")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderEdit {
    @Id
    private String id;
    private String orderId;    // Links to the specific KitchenOrder
    private String hotelId;
    private String editedBy;   // Name of Waiter/Admin
    private String itemName;
    private int previousQty;
    private int newQty;
    private int delta;         // The difference (e.g., +2, -1)
    private String timestamp;  // IST Time
}