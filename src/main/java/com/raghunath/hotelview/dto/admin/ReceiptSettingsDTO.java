package com.raghunath.hotelview.dto.admin;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptSettingsDTO {
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
}