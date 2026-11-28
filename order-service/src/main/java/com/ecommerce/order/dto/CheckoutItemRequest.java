package com.ecommerce.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutItemRequest {

    @NotNull(message = "variantId is required")
    private Long variantId;

    @Min(value = 1, message = "quantity must be at least 1")
    private int quantity;

}
