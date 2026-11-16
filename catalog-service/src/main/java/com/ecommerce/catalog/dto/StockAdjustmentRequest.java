package com.ecommerce.catalog.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * A manual stock correction: {@code 10} for a delivery received, {@code -1}
 * for a broken unit. Bounded so that the stock can never overflow.
 */
public record StockAdjustmentRequest(
        @NotNull(message = "delta is required")
        @Min(value = -1_000_000, message = "delta must be between -1000000 and 1000000")
        @Max(value = 1_000_000, message = "delta must be between -1000000 and 1000000")
        Integer delta) {
}
