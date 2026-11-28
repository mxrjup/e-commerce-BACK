package com.ecommerce.order.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderItemTest {

    @Test
    @DisplayName("Should calculate subtotal HT correctly (unitPrice * quantity)")
    void shouldCalculateSubTotalExclTax_nominal() {
        OrderItem item = new OrderItem(null, 1L, "Clavier", new BigDecimal("49.90"), new BigDecimal("20.00"), 2);

        BigDecimal subTotalHt = item.getSubTotalExclTax();

        assertThat(subTotalHt).isEqualByComparingTo(new BigDecimal("99.80"));
    }

    @Test
    @DisplayName("Should return 0.00 for HT when unitPriceExclTax is null")
    void shouldReturnZeroSubTotalExclTax_whenUnitPriceIsNull() {
        OrderItem item = new OrderItem(null, 1L, "Article", null, new BigDecimal("20.00"), 2);

        BigDecimal subTotalHt = item.getSubTotalExclTax();

        assertThat(subTotalHt).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should calculate subtotal TTC with standard VAT 20%")
    void shouldCalculateSubTotalInclTax_standardVat20() {
        OrderItem item = new OrderItem(null, 1L, "Écran", new BigDecimal("100.00"), new BigDecimal("20.00"), 1);

        BigDecimal subTotalTtc = item.getSubTotalInclTax();

        // 100 HT + 20% TVA = 120 TTC
        assertThat(subTotalTtc).isEqualByComparingTo(new BigDecimal("120.00"));
    }

    @Test
    @DisplayName("Should calculate subtotal TTC with reduced VAT 5.5%")
    void shouldCalculateSubTotalInclTax_reducedVat5_5() {
        OrderItem item = new OrderItem(null, 1L, "Livre", new BigDecimal("20.00"), new BigDecimal("5.50"), 1);

        BigDecimal subTotalTtc = item.getSubTotalInclTax();

        // 20 HT + (20 * 0.055 = 1.10 TVA) = 21.10 TTC
        assertThat(subTotalTtc).isEqualByComparingTo(new BigDecimal("21.10"));
    }

    @Test
    @DisplayName("Should round VAT correctly with HALF_UP strategy")
    void shouldRoundVatWithHalfUp() {
        // 10.05 * 5.5% = 0.55275 -> arrondi à 0.55
        // TTC = 10.05 + 0.55 = 10.60
        OrderItem item = new OrderItem(null, 1L, "Produit", new BigDecimal("10.05"), new BigDecimal("5.50"), 1);

        BigDecimal subTotalTtc = item.getSubTotalInclTax();

        assertThat(subTotalTtc).isEqualByComparingTo(new BigDecimal("10.60"));
    }

    @Test
    @DisplayName("Should return HT when VAT rate is null")
    void shouldReturnSubTotalHt_whenVatRateIsNull() {
        OrderItem item = new OrderItem(null, 1L, "Article exempt", new BigDecimal("50.00"), null, 1);

        BigDecimal subTotalTtc = item.getSubTotalInclTax();

        assertThat(subTotalTtc).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    @DisplayName("Should return HT when VAT rate is 0.00%")
    void shouldReturnSubTotalHt_whenVatRateIsZero() {
        OrderItem item = new OrderItem(null, 1L, "Article 0%", new BigDecimal("75.00"), BigDecimal.ZERO, 1);

        BigDecimal subTotalTtc = item.getSubTotalInclTax();

        assertThat(subTotalTtc).isEqualByComparingTo(new BigDecimal("75.00"));
    }
}
