package com.ecommerce.catalog.dto;

import com.ecommerce.catalog.entity.Product;
import com.ecommerce.catalog.entity.ProductStatus;
import com.ecommerce.catalog.entity.Variant;
import com.ecommerce.catalog.support.Designations;

/**
 * A line of the back-office inventory (C07, screen C20): the exact stock of
 * a variant and its alert threshold, which the storefront never shows.
 *
 * @param lowStock the stock is at or below the alert threshold
 */
public record InventoryItemResponse(
        Long variantId,
        String sku,
        String designation,
        Long productId,
        String productSlug,
        ProductStatus productStatus,
        int stock,
        int alertThreshold,
        boolean lowStock) {

    public static InventoryItemResponse from(Variant variant) {
        Product product = variant.getProduct();
        return new InventoryItemResponse(
                variant.getId(),
                variant.getSku(),
                Designations.of(variant),
                product.getId(),
                product.getSlug(),
                product.getStatus(),
                variant.getStock(),
                variant.getAlertThreshold(),
                variant.isLowOnStock());
    }
}
