package com.ecommerce.catalog.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** At or below this stock level, the variant shows up in the inventory alerts. */
public record AlertThresholdRequest(
        @NotNull(message = "alertThreshold is required")
        @PositiveOrZero(message = "alertThreshold must be positive or zero")
        @Max(value = 1_000_000, message = "alertThreshold must be at most 1000000")
        Integer alertThreshold) {
}
