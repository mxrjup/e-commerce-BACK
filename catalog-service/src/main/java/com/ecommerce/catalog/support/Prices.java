package com.ecommerce.catalog.support;

import com.ecommerce.catalog.entity.Variant;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The prices the storefront shows, including VAT.
 *
 * <p>Same rounding as order-service for an order line ({@code OrderItem}):
 * the VAT amount is rounded to the cent, then added to the price excluding
 * VAT. The storefront therefore shows exactly what the checkout charges.
 */
public final class Prices {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Prices() {
    }

    public static BigDecimal inclTax(BigDecimal exclTax, BigDecimal vatRate) {
        BigDecimal vat = exclTax.multiply(vatRate).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        return exclTax.add(vat).setScale(2, RoundingMode.HALF_UP);
    }

    /** What the customer pays for one unit: the discount when there is one, plus the eco-tax, plus VAT. */
    public static BigDecimal currentInclTax(Variant variant, BigDecimal vatRate) {
        return inclTax(variant.currentPriceExclTax().add(ecoTax(variant)), vatRate);
    }

    /** The struck-through price when the variant is on sale, null otherwise. */
    public static BigDecimal regularInclTax(Variant variant, BigDecimal vatRate) {
        return variant.getDiscountPriceExclTax() == null ? null
                : inclTax(variant.getSalePriceExclTax().add(ecoTax(variant)), vatRate);
    }

    /** The eco-tax share of the price, VAT included; null when the variant is not subject to it. */
    public static BigDecimal ecoTaxInclTax(Variant variant, BigDecimal vatRate) {
        return variant.getEcoTax() == null ? null : inclTax(variant.getEcoTax(), vatRate);
    }

    private static BigDecimal ecoTax(Variant variant) {
        return variant.getEcoTax() != null ? variant.getEcoTax() : BigDecimal.ZERO;
    }
}
