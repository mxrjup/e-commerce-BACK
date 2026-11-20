package com.ecommerce.catalog.dto;

import com.ecommerce.catalog.entity.Product;
import com.ecommerce.catalog.entity.ProductStatus;
import com.ecommerce.catalog.entity.Variant;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;

/**
 * A row of the back-office product list (C15).
 *
 * @param priceFromExclTax the lowest current price of the variants, excluding VAT; null without variant
 * @param totalStock       the units of all the variants together
 * @param lowStock         at least one variant is at or below its alert threshold
 */
public record AdminProductSummary(
        Long id,
        String title,
        String slug,
        ProductStatus status,
        AdminProductResponse.Reference brand,
        AdminProductResponse.Reference category,
        int variantCount,
        BigDecimal priceFromExclTax,
        long totalStock,
        boolean lowStock,
        Instant updatedAt) {

    public static AdminProductSummary from(Product product) {
        return new AdminProductSummary(
                product.getId(),
                product.getTitle(),
                product.getSlug(),
                product.getStatus(),
                new AdminProductResponse.Reference(product.getBrand().getId(), product.getBrand().getName()),
                new AdminProductResponse.Reference(product.getCategory().getId(), product.getCategory().getName()),
                product.getVariants().size(),
                product.getVariants().stream()
                        .map(Variant::currentPriceExclTax)
                        .min(Comparator.naturalOrder())
                        .orElse(null),
                product.getVariants().stream().mapToLong(Variant::getStock).sum(),
                product.getVariants().stream().anyMatch(Variant::isLowOnStock),
                product.getUpdatedAt());
    }
}
