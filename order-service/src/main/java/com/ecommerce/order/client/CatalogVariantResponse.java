package com.ecommerce.order.client;

import java.math.BigDecimal;

public record CatalogVariantResponse(
    Long id,
    String designation,
    BigDecimal unitPriceExclTax,
    BigDecimal vatRate
) {}
