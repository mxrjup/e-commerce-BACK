package com.ecommerce.catalog.dto;

import com.ecommerce.catalog.entity.AttributeValue;
import com.ecommerce.catalog.entity.Variant;
import com.ecommerce.catalog.support.Designations;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * A variant as the back-office sees it: prices excluding VAT, purchase price
 * and exact stock included, which the storefront never shows.
 *
 * @param values the attribute values, in the order of their attributes
 */
public record AdminVariantResponse(
        Long id,
        Long productId,
        String sku,
        String designation,
        BigDecimal purchasePriceExclTax,
        BigDecimal salePriceExclTax,
        BigDecimal discountPriceExclTax,
        BigDecimal ecoTax,
        BigDecimal weightKg,
        int stock,
        int alertThreshold,
        boolean lowStock,
        List<Value> values,
        List<Image> images,
        Instant createdAt,
        Instant updatedAt) {

    public record Value(Long attributeId, String attribute, Long valueId, String value) {
    }

    public record Image(Long id, String url, int position) {
    }

    public static AdminVariantResponse from(Variant variant) {
        return new AdminVariantResponse(
                variant.getId(),
                variant.getProduct().getId(),
                variant.getSku(),
                Designations.of(variant),
                variant.getPurchasePriceExclTax(),
                variant.getSalePriceExclTax(),
                variant.getDiscountPriceExclTax(),
                variant.getEcoTax(),
                variant.getWeightKg(),
                variant.getStock(),
                variant.getAlertThreshold(),
                variant.isLowOnStock(),
                variant.getValues().stream()
                        .sorted(Comparator.comparing((AttributeValue value) -> value.getAttribute().getId())
                                .thenComparing(AttributeValue::getId))
                        .map(value -> new Value(value.getAttribute().getId(), value.getAttribute().getName(),
                                value.getId(), value.getValue()))
                        .toList(),
                variant.getImages().stream()
                        .map(image -> new Image(image.getId(), image.getUrl(), image.getPosition()))
                        .toList(),
                variant.getCreatedAt(),
                variant.getUpdatedAt());
    }
}
