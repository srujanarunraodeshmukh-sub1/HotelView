package com.raghunath.hotelview.dto.admin;

import lombok.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InstantCheckoutRequest {
    @NotEmpty(message = "Items list cannot be empty")
    @Valid
    private List<OrderItem> items;

    @NotNull(message = "Discount percent is required")
    private Double discountPercent;
}