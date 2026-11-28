package com.ecommerce.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record OrderStatsResponse(
        Instant from, Instant to, Granularity granularity, Ranking ranking,
        BigDecimal revenueExclTax, BigDecimal revenueInclTax, long orderCount,
        BigDecimal averageBasket, BigDecimal margin, BigDecimal knownMargin,
        boolean marginComplete, boolean revenueComplete, boolean topProductsComplete,
        List<RevenuePoint> curve, List<TopProduct> topProducts, List<Alert> alerts) {
    public enum Granularity { DAY, WEEK, MONTH }
    public enum Ranking { QUANTITY, REVENUE }
    public record RevenuePoint(LocalDate date, BigDecimal revenueExclTax,
                               BigDecimal revenueInclTax, long orderCount) {}
    public record TopProduct(Long productId, long quantity, BigDecimal revenueExclTax) {}
    public record Alert(String code, long count) {}
}
