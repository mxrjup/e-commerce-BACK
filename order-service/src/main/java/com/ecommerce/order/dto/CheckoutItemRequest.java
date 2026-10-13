package com.ecommerce.order.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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

    @NotBlank(message = "designation is required")
    private String designation;

    @NotNull(message = "unitPriceExclTax is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "unitPriceExclTax must be greater than 0")
    private BigDecimal unitPriceExclTax;

    @NotNull(message = "vatRate is required")
    @DecimalMin(value = "0.0", message = "vatRate must be positive or zero")
    private BigDecimal vatRate;
    
    @Min(value = 1, message = "quantity must be at least 1")
    private int quantity;

}
