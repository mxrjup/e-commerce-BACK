package com.ecommerce.cart.client;

import java.util.Optional;

public interface CatalogClient {
    Optional<CatalogVariantResponse> getVariant(Long variantId);
}
