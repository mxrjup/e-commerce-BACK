package com.ecommerce.catalog.dto;

import com.ecommerce.catalog.entity.AttributeValue;
import com.ecommerce.catalog.entity.Product;
import com.ecommerce.catalog.entity.ProductStatus;
import com.ecommerce.catalog.entity.Variant;
import com.ecommerce.catalog.entity.VariantImage;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * A variant as the other services see it (C10): what Order freezes in an
 * order line, what Cart displays, what the MCP tools read.
 *
 * <p>{@code id}, {@code designation}, {@code unitPriceExclTax} and
 * {@code vatRate} are read by order-service under these exact names: renaming
 * one breaks the checkout.
 *
 * @param designation          product title and variant values: "Logitech G915 TKL - Noir, AZERTY"
 * @param unitPriceExclTax     what the customer pays before VAT: the discount when there is one,
 *                             plus the eco-tax, which is part of the price
 * @param purchasePriceExclTax internal only, for the margins: never on the public API
 * @param available            the product is published and the variant has stock
 * @param imageUrl             the main photo, or null
 */
public record InternalVariantResponse(
        Long id,
        Long productId,
        String productSlug,
        String sku,
        String designation,
        BigDecimal unitPriceExclTax,
        BigDecimal salePriceExclTax,
        BigDecimal discountPriceExclTax,
        BigDecimal ecoTax,
        BigDecimal vatRate,
        BigDecimal purchasePriceExclTax,
        BigDecimal weightKg,
        int stock,
        ProductStatus status,
        boolean available,
        String imageUrl) {

    public static InternalVariantResponse from(Variant variant) {
        Product product = variant.getProduct();
        BigDecimal ecoTax = variant.getEcoTax() != null ? variant.getEcoTax() : BigDecimal.ZERO;
        return new InternalVariantResponse(
                variant.getId(),
                product.getId(),
                product.getSlug(),
                variant.getSku(),
                designation(variant),
                variant.currentPriceExclTax().add(ecoTax),
                variant.getSalePriceExclTax(),
                variant.getDiscountPriceExclTax(),
                variant.getEcoTax(),
                product.getVatRate().getRate(),
                variant.getPurchasePriceExclTax(),
                variant.getWeightKg(),
                variant.getStock(),
                product.getStatus(),
                product.getStatus() == ProductStatus.PUBLIE && variant.getStock() > 0,
                variant.getImages().stream().findFirst().map(VariantImage::getUrl).orElse(null));
    }

    /** The values in the order their attributes were created, so a designation never changes between calls. */
    private static String designation(Variant variant) {
        String values = variant.getValues().stream()
                .sorted(Comparator.comparing((AttributeValue value) -> value.getAttribute().getId())
                        .thenComparing(AttributeValue::getId))
                .map(AttributeValue::getValue)
                .collect(Collectors.joining(", "));
        String title = variant.getProduct().getTitle();
        return values.isEmpty() ? title : title + " - " + values;
    }
}
