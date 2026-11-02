package com.ecommerce.order.client;

public interface CatalogClient {
    CatalogVariantResponse getVariant(Long variantId);
}
