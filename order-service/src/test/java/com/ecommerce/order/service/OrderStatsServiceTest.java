package com.ecommerce.order.service;

import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.order.client.*;
import com.ecommerce.order.dto.OrderStatsResponse.*;
import com.ecommerce.order.repository.OrderStatsRepository;
import com.ecommerce.order.repository.OrderStatsRepository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.LongStream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderStatsServiceTest {
    private final OrderStatsRepository repository = mock(OrderStatsRepository.class);
    private final CatalogClient catalog = mock(CatalogClient.class);
    private final OrderStatsService service = new OrderStatsService(repository, catalog);
    private final Instant from = Instant.parse("2026-01-01T00:00:00Z");
    private final Instant to = Instant.parse("2026-01-03T00:00:00Z");

    @BeforeEach
    void setup() {
        when(repository.totals(from, to)).thenReturn(new Totals(new BigDecimal("100"), new BigDecimal("125"), 3, 0, 0));
        when(repository.margin(from, to)).thenReturn(new Margin(new BigDecimal("40"), 0));
    }

    @Test
    void computesRoundedBasketAndFillsEmptyDays() {
        var result = service.stats(from, to, Granularity.DAY, Ranking.QUANTITY);
        assertThat(result.averageBasket()).isEqualByComparingTo("41.67");
        assertThat(result.margin()).isEqualByComparingTo("40");
        assertThat(result.marginComplete()).isTrue();
        assertThat(result.curve()).hasSize(2);
        assertThat(result.curve().getFirst().date()).hasToString("2026-01-01");
        assertThat(result.curve().getFirst().revenueInclTax()).isEqualByComparingTo("0");
        verifyNoInteractions(catalog);
    }

    @Test
    void emptyPeriodHasZeroBasketAndMargin() {
        when(repository.totals(from, to)).thenReturn(new Totals(BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, 0));
        when(repository.margin(from, to)).thenReturn(new Margin(BigDecimal.ZERO, 0));
        var result = service.stats(from, to, Granularity.MONTH, Ranking.QUANTITY);
        assertThat(result.averageBasket()).isEqualByComparingTo("0");
        assertThat(result.margin()).isEqualByComparingTo("0");
        assertThat(result.topProducts()).isEmpty();
        assertThat(result.alerts()).isEmpty();
        assertThat(result.curve()).hasSize(1);
    }

    @Test
    void exposesIncompleteMarginWithoutInventingCosts() {
        when(repository.margin(from, to)).thenReturn(new Margin(new BigDecimal("40"), 2));
        when(repository.totals(from, to)).thenReturn(new Totals(BigDecimal.ZERO, BigDecimal.ZERO, 1, 1, 2));
        var result = service.stats(from, to, Granularity.DAY, Ranking.QUANTITY);
        assertThat(result.margin()).isNull();
        assertThat(result.knownMargin()).isEqualByComparingTo("40");
        assertThat(result.marginComplete()).isFalse();
        assertThat(result.revenueComplete()).isFalse();
        assertThat(result.alerts()).contains(new Alert("MISSING_PURCHASE_PRICE", 2),
                new Alert("MISSING_ORDER_TOTALS", 1), new Alert("PENDING_PAYMENT", 2));
    }

    @Test
    void mergesAllVariantsBeforeSelectingFiveProductsAndBreaksTiesById() {
        when(repository.variantSales(from, to)).thenReturn(List.of(
                new VariantSales(1L, 4, new BigDecimal("4")), new VariantSales(2L, 4, new BigDecimal("4")),
                new VariantSales(3L, 7, new BigDecimal("70")), new VariantSales(4L, 6, new BigDecimal("60")),
                new VariantSales(5L, 5, new BigDecimal("50")), new VariantSales(6L, 5, new BigDecimal("50")),
                new VariantSales(7L, 2, new BigDecimal("200"))));
        when(catalog.getProductMappings(anyList())).thenReturn(List.of(
                new CatalogProductMapping(1L, 10L), new CatalogProductMapping(2L, 10L),
                new CatalogProductMapping(3L, 20L), new CatalogProductMapping(4L, 30L),
                new CatalogProductMapping(5L, 40L), new CatalogProductMapping(6L, 50L), new CatalogProductMapping(7L, 60L)));
        var quantities = service.stats(from, to, Granularity.DAY, Ranking.QUANTITY);
        assertThat(quantities.topProducts()).extracting(TopProduct::productId).containsExactly(10L, 20L, 30L, 40L, 50L);
        assertThat(quantities.topProducts().getFirst().quantity()).isEqualTo(8);
        var revenue = service.stats(from, to, Granularity.DAY, Ranking.REVENUE);
        assertThat(revenue.topProducts()).extracting(TopProduct::productId).containsExactly(60L, 20L, 30L, 40L, 50L);
    }

    @Test
    void batchesMappingsAndFlagsDeletedVariants() {
        when(repository.variantSales(from, to)).thenReturn(LongStream.rangeClosed(1, 101)
                .mapToObj(id -> new VariantSales(id, 1, BigDecimal.ONE)).toList());
        when(catalog.getProductMappings(anyList())).thenReturn(List.of());
        var result = service.stats(from, to, Granularity.DAY, Ranking.QUANTITY);
        assertThat(result.topProductsComplete()).isFalse();
        assertThat(result.alerts()).contains(new Alert("UNRESOLVED_PRODUCT_VARIANTS", 101));
        verify(catalog).getProductMappings(LongStream.rangeClosed(1, 100).boxed().toList());
        verify(catalog).getProductMappings(List.of(101L));
    }

    @Test
    void catalogFailureDoesNotDiscardFinancialStats() {
        when(repository.variantSales(from, to)).thenReturn(List.of(new VariantSales(1L, 1, BigDecimal.TEN)));
        when(catalog.getProductMappings(anyList())).thenThrow(new ResourceAccessException("offline"));
        var result = service.stats(from, to, Granularity.DAY, Ranking.QUANTITY);
        assertThat(result.revenueInclTax()).isEqualByComparingTo("125");
        assertThat(result.topProductsComplete()).isFalse();
        assertThat(result.alerts()).contains(new Alert("CATALOG_UNAVAILABLE", 1));
    }

    @Test
    void rejectsReversedEmptyOrExcessivePeriodsBeforeQuerying() {
        assertThatThrownBy(() -> service.stats(to, from, Granularity.DAY, Ranking.QUANTITY)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.stats(from, from, Granularity.DAY, Ranking.QUANTITY)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.stats(from, Instant.parse("2030-01-01T00:00:00Z"), Granularity.DAY, Ranking.QUANTITY)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(repository, catalog);
    }
}
