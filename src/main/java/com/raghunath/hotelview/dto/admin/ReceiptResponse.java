package com.raghunath.hotelview.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ReceiptResponse {
    // Restaurant Header
    private String restaurantName;
    private String restaurantAddress;
    private String restaurantContact;
    private String restaurantLogo;
    private String restaurantUpi;


    // Order Data
    private String orderId;
    private String date;
    private String time;
    private String orderType;
    private List<FlattenedItem> items;

    // --- UPDATED PRICING SECTION ---
    private Double grandTotal;     // The original sum (e.g., 1000)
    private Double discountPercent; // The % entered (e.g., 10)
    private Double discountAmount;  // The calculated deduction (e.g., 100)
    private Double totalPayable;    // The final amount after discount (e.g., 900)
    // -------------------------------

    // Customer Info
    private String customerName;
    private String customerMobile;
    private String customerAddress;

    @Data
    @Builder
    public static class FlattenedItem {
        private String itemName;
        private Integer quantity;
        private Double price;
        private Double subTotal;
    }
}