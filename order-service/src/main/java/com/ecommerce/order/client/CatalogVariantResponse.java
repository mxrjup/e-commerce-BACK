package com.ecommerce.order.client;

import java.math.BigDecimal;

public record CatalogVariantResponse(
    Long id,
    String designation,
    BigDecimal unitPriceExclTax,
    BigDecimal vatRate,
    BigDecimal purchasePriceExclTax,
    int stock,
    boolean available
) {
    public CatalogVariantResponse(
        Long id,
        String designation,
        BigDecimal unitPriceExclTax,
        BigDecimal vatRate
    ) {
        this(id, designation, unitPriceExclTax, vatRate, BigDecimal.ZERO, Integer.MAX_VALUE, true);
    }
}
