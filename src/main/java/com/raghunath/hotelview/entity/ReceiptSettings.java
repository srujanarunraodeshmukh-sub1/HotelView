package com.raghunath.hotelview.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "receipt_settings")
public class ReceiptSettings {

    @Id
    private String id;
    private String hotelId;
    private String receiptType; // "TABLE_HOME" or "INSTANT"

    private boolean printRestaurantName;
    private boolean printBusinessAddress;
    private boolean printCustomerNumber;
    private boolean includeRestaurantLogo;
    private boolean printDateTime;
    private boolean showOrderTypeLabel;
    private boolean displayOrderId;
    private boolean showCustomerDetails;
    private boolean printUpiAndQr;
    private boolean printGreetingNote;
    private boolean printItemQuantities;
}