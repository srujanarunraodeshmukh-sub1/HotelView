package com.raghunath.hotelview.dto.admin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@ToString // 💡 Added for better logging
public class OrderItem {

    @NotBlank(message = "Item ID is required")
    private String itemId; // 👈 Add this field to match your JSON

    @NotBlank(message = "Item name is required")
    private String itemName;

    @Min(value = 1, message = "Quantity must be at least 1")
    private int quantity;

    @NotNull(message = "Price is required")
    @Min(value = 0, message = "Price cannot be negative")
    private Double price;

    private Double subTotal;

    public Double getSubTotal() {
        if (this.price == null) return 0.0;
        this.subTotal = this.quantity * this.price;
        return this.subTotal;
    }
}