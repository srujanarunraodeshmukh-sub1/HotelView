package com.raghunath.hotelview.dto.admin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class OrderItem {

    @NotBlank(message = "Item ID is required")
    private String itemId;

    @NotBlank(message = "Item name is required")
    private String itemName;

    // 🚀 Changed to String to allow measurements like "1000 ml" or "2 plates"
    // Removed @Min since it can't validate text fields
    @NotBlank(message = "Quantity/Volume description is required")
    private String quantity;

    @NotNull(message = "Price is required")
    @Min(value = 0, message = "Price cannot be negative")
    private Double price;

    @NotNull(message = "Subtotal is required")
    @Min(value = 0, message = "Subtotal cannot be negative")
    private Double subTotal;

    // 🚀 Cleaned up getter to return the submitted frontend subtotal calculation safely
    public Double getSubTotal() {
        return this.subTotal != null ? this.subTotal : 0.0;
    }
}