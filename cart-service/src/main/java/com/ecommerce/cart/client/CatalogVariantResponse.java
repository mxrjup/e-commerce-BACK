package com.ecommerce.cart.client;

import java.math.BigDecimal;

public record CatalogVariantResponse(
        Long id,
        String designation,
        BigDecimal unitPriceExclTax,
        BigDecimal vatRate,
        boolean available,
        String imageUrl
) {
}
