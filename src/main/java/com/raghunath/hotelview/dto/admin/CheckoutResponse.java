package com.raghunath.hotelview.dto.admin;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class CheckoutResponse {
    private String id;
    private String shortId; // Last 6 digits
    private String checkoutDate;
    private String checkoutTime;
    private String orderType;
    private String tableName;
    private String customerName;
    private String customerMobile;
    private String customerAddress;
    private List<BillItem> items;
    private Double grandTotal;
    private Double totalPayable;

    @Data
    @Builder
    public static class BillItem {
        private String itemName;
        private String quantity;
        private Double price; // unit price * quantity
    }
}