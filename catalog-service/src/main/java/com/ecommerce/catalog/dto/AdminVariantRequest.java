package com.ecommerce.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Creates or replaces a variant (C04, screen C17). Prices are excluding VAT.
 *
 * <p>{@code initialStock} and {@code alertThreshold} are accepted at creation
 * only: afterwards, the stock moves through the stock adjustments (C07) and
 * the paid orders (C21), never through this form. A form opened five minutes
 * ago would otherwise write back an old stock and erase the sales made since.
 *
 * @param discountPriceExclTax null when the variant is not on sale; below the sale price
 * @param ecoTax               null when the variant is not subject to it
 * @param valueIds             one value per attribute at most; empty for a product without choices
 * @param initialStock         at creation only, 0 when absent
 * @param alertThreshold       at creation only, 0 when absent
 */
public record AdminVariantRequest(
        @NotBlank(message = "sku must not be blank")
        @Size(max = 64, message = "sku must be at most 64 characters")
        String sku,

        @NotNull(message = "purchasePriceExclTax is required")
        @DecimalMin(value = "0", message = "purchasePriceExclTax must be positive or zero")
        @Digits(integer = 8, fraction = 2, message = "purchasePriceExclTax must have at most 8 digits and 2 decimals")
        BigDecimal purchasePriceExclTax,

        @NotNull(message = "salePriceExclTax is required")
        @DecimalMin(value = "0", message = "salePriceExclTax must be positive or zero")
        @Digits(integer = 8, fraction = 2, message = "salePriceExclTax must have at most 8 digits and 2 decimals")
        BigDecimal salePriceExclTax,

        @DecimalMin(value = "0", message = "discountPriceExclTax must be positive or zero")
        @Digits(integer = 8, fraction = 2, message = "discountPriceExclTax must have at most 8 digits and 2 decimals")
        BigDecimal discountPriceExclTax,

        @DecimalMin(value = "0", message = "ecoTax must be positive or zero")
        @Digits(integer = 8, fraction = 2, message = "ecoTax must have at most 8 digits and 2 decimals")
        BigDecimal ecoTax,

        @NotNull(message = "weightKg is required")
        @DecimalMin(value = "0", message = "weightKg must be positive or zero")
        @Digits(integer = 5, fraction = 3, message = "weightKg must have at most 5 digits and 3 decimals")
        BigDecimal weightKg,

        @NotNull(message = "valueIds is required, empty for a product without choices")
        @Size(max = 10, message = "a variant has at most 10 attribute values")
        List<@NotNull(message = "valueIds must not contain null") Long> valueIds,

        @PositiveOrZero(message = "initialStock must be positive or zero")
        @Max(value = 1_000_000, message = "initialStock must be at most 1000000")
        Integer initialStock,

        @PositiveOrZero(message = "alertThreshold must be positive or zero")
        @Max(value = 1_000_000, message = "alertThreshold must be at most 1000000")
        Integer alertThreshold) {
}
