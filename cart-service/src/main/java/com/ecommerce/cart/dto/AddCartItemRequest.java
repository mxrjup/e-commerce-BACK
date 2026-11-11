package com.ecommerce.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddCartItemRequest {

    @NotNull(message = "variantId is mandatory")
    private Long variantId;

    @NotNull(message = "quantity is mandatory")
    @Min(value = 1, message = "quantity must be at least 1")
    private Integer quantity;
}
