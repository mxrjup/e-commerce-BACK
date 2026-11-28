package com.ecommerce.order.client;

import java.util.List;

public interface CatalogClient {
    CatalogVariantResponse getVariant(Long variantId);
    List<CatalogProductMapping> getProductMappings(List<Long> ids);
}
