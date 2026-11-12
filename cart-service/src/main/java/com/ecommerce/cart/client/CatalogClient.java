package com.ecommerce.cart.client;

import java.util.List;
import java.util.Optional;

public interface CatalogClient {
    Optional<CatalogVariantResponse> getVariant(Long variantId);

    default List<CatalogVariantResponse> getVariants(List<Long> variantIds) {
        return variantIds.stream()
                .map(this::getVariant)
                .flatMap(Optional::stream)
                .toList();
    }
}
