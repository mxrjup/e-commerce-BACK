package com.ecommerce.catalog.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Creates or replaces a VAT rate. {@code rate} is a percentage: 20.00 for 20 %.
 * Marking a rate as default takes the flag off the previous default.
 */
public record VatRateRequest(
        @NotBlank(message = "label must not be blank")
        @Size(max = 50, message = "label must be at most 50 characters")
        String label,

        @NotNull(message = "rate is required")
        @DecimalMin(value = "0.00", message = "rate must be between 0 and 100")
        @DecimalMax(value = "100.00", message = "rate must be between 0 and 100")
        @Digits(integer = 3, fraction = 2, message = "rate must have at most 2 decimals")
        BigDecimal rate,

        boolean defaultRate) {
}
