package com.ecommerce.cart.client;

import java.math.BigDecimal;

public record CatalogVariantResponse(
        Long id,
        String designation,
        BigDecimal currentPrice,
        boolean available,
        String imageUrl
) {
}
