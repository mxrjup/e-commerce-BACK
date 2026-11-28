package com.ecommerce.order.service;

import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.order.client.CatalogClient;
import com.ecommerce.order.client.CatalogProductMapping;
import com.ecommerce.order.dto.OrderStatsResponse;
import com.ecommerce.order.dto.OrderStatsResponse.*;
import com.ecommerce.order.repository.OrderStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderStatsService {
    private final OrderStatsRepository repository;
    private final CatalogClient catalog;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public OrderStatsResponse stats(Instant from, Instant to, Granularity granularity, Ranking ranking) {
        if (from == null || to == null || !from.isBefore(to)
                || from.isBefore(Instant.parse("0001-01-01T00:00:00Z"))
                || to.isAfter(Instant.parse("9999-12-31T00:00:00Z"))) {
            throw new BadRequestException("from and to must be ISO timestamps with from < to (years 0001 to 9999)");
        }
        LocalDate first = bucket(from, granularity);
        LocalDate last = bucket(to.minusNanos(1), granularity);
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate date = first; !date.isAfter(last); date = next(date, granularity)) {
            if (dates.size() == 1000) {
                throw new BadRequestException("The period must contain at most 1000 buckets; choose a coarser granularity");
            }
            dates.add(date);
        }
        var totals = repository.totals(from, to);
        var margin = repository.margin(from, to);
        var buckets = repository.curve(from, to, granularity.name().toLowerCase(Locale.ROOT)).stream()
                .collect(Collectors.toMap(OrderStatsRepository.Bucket::date, Function.identity()));
        List<RevenuePoint> curve = dates.stream().map(date -> {
            var value = buckets.get(date);
            return value == null ? new RevenuePoint(date, money(BigDecimal.ZERO), money(BigDecimal.ZERO), 0)
                    : new RevenuePoint(date, money(value.revenueExclTax()), money(value.revenueInclTax()), value.orderCount());
        }).toList();
        List<Alert> alerts = new ArrayList<>();
        alert(alerts, "PENDING_PAYMENT", totals.pendingOrders());
        alert(alerts, "MISSING_ORDER_TOTALS", totals.missingTotals());
        alert(alerts, "MISSING_PURCHASE_PRICE", margin.missingPurchasePrices());
        var sales = repository.variantSales(from, to);
        Map<Long, Long> productIds = new HashMap<>();
        boolean catalogueUnavailable = false;
        for (int start = 0; start < sales.size(); start += 100) {
            List<Long> ids = sales.subList(start, Math.min(start + 100, sales.size())).stream()
                    .map(OrderStatsRepository.VariantSales::variantId).toList();
            try {
                List<CatalogProductMapping> mappings = catalog.getProductMappings(ids);
                if (mappings == null) {
                    catalogueUnavailable = true;
                    break;
                }
                mappings.stream().filter(m -> m.id() != null && m.productId() != null && ids.contains(m.id()))
                        .forEach(m -> productIds.put(m.id(), m.productId()));
            } catch (RestClientException ex) {
                catalogueUnavailable = true;
                break;
            }
        }
        Map<Long, TopProduct> products = new HashMap<>();
        long unresolved = 0;
        for (var sale : sales) {
            Long productId = productIds.get(sale.variantId());
            if (productId == null) {
                unresolved++;
                continue;
            }
            products.merge(productId, new TopProduct(productId, sale.quantity(), money(sale.revenueExclTax())),
                    (a, b) -> new TopProduct(a.productId(), a.quantity() + b.quantity(), a.revenueExclTax().add(b.revenueExclTax())));
        }
        alert(alerts, "UNRESOLVED_PRODUCT_VARIANTS", unresolved);
        alert(alerts, "CATALOG_UNAVAILABLE", catalogueUnavailable ? 1 : 0);
        Comparator<TopProduct> comparator = ranking == Ranking.QUANTITY
                ? Comparator.comparingLong(TopProduct::quantity).reversed()
                : Comparator.comparing(TopProduct::revenueExclTax).reversed();
        List<TopProduct> top = products.values().stream().sorted(comparator.thenComparing(TopProduct::productId))
                .limit(5).toList();
        boolean completeMargin = margin.missingPurchasePrices() == 0;
        return new OrderStatsResponse(from, to, granularity, ranking,
                money(totals.revenueExclTax()), money(totals.revenueInclTax()), totals.orderCount(),
                totals.orderCount() == 0 ? money(BigDecimal.ZERO)
                        : totals.revenueInclTax().divide(BigDecimal.valueOf(totals.orderCount()), 2, RoundingMode.HALF_UP),
                completeMargin ? money(margin.knownMargin()) : null, money(margin.knownMargin()),
                completeMargin, totals.missingTotals() == 0, unresolved == 0 && !catalogueUnavailable,
                curve, top, alerts);
    }

    private static BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
    private static void alert(List<Alert> alerts, String code, long count) {
        if (count > 0) alerts.add(new Alert(code, count));
    }
    private static LocalDate bucket(Instant instant, Granularity granularity) {
        LocalDate date = instant.atOffset(ZoneOffset.UTC).toLocalDate();
        return switch (granularity) {
            case DAY -> date;
            case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> date.withDayOfMonth(1);
        };
    }
    private static LocalDate next(LocalDate date, Granularity granularity) {
        return switch (granularity) {
            case DAY -> date.plusDays(1);
            case WEEK -> date.plusWeeks(1);
            case MONTH -> date.plusMonths(1);
        };
    }
}
