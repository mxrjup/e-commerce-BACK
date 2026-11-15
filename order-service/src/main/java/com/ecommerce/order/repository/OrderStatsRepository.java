package com.ecommerce.order.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class OrderStatsRepository {
    private static final String PAID = "o.statut IN ('IN_PREPARATION','SHIPPED','DELIVERED')";
    private static final String PERIOD = "o.date_creation >= ? AND o.date_creation < ?";
    private final JdbcTemplate jdbc;

    public record Totals(BigDecimal revenueExclTax, BigDecimal revenueInclTax,
                         long orderCount, long missingTotals, long pendingOrders) {}
    public record Margin(BigDecimal knownMargin, long missingPurchasePrices) {}
    public record Bucket(LocalDate date, BigDecimal revenueExclTax,
                         BigDecimal revenueInclTax, long orderCount) {}
    public record VariantSales(Long variantId, long quantity, BigDecimal revenueExclTax) {}

    public Totals totals(Instant from, Instant to) {
        return jdbc.queryForObject("SELECT "
                + "COALESCE(SUM(CASE WHEN " + PAID + " THEN o.total_ht END),0) ht, "
                + "COALESCE(SUM(CASE WHEN " + PAID + " THEN o.total_ttc END),0) ttc, "
                + "COUNT(CASE WHEN " + PAID + " THEN 1 END) orders, "
                + "COUNT(CASE WHEN " + PAID + " AND (o.total_ht IS NULL OR o.total_ttc IS NULL) THEN 1 END) missing, "
                + "COUNT(CASE WHEN o.statut='NEW' THEN 1 END) pending "
                + "FROM commande o WHERE " + PERIOD,
                (rs, n) -> new Totals(rs.getBigDecimal("ht"), rs.getBigDecimal("ttc"),
                        rs.getLong("orders"), rs.getLong("missing"), rs.getLong("pending")), bounds(from, to));
    }

    public Margin margin(Instant from, Instant to) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM((i.prix_unitaire_ht-i.prix_achat_ht)*i.quantite),0) margin,
                       COUNT(CASE WHEN i.prix_achat_ht IS NULL THEN 1 END) missing
                FROM ligne_commande i JOIN commande o ON o.id=i.commande_id WHERE
                """ + PAID + " AND " + PERIOD,
                (rs, n) -> new Margin(rs.getBigDecimal("margin"), rs.getLong("missing")), bounds(from, to));
    }

    public List<Bucket> curve(Instant from, Instant to, String granularity) {
        if (!List.of("day", "week", "month").contains(granularity)) {
            throw new IllegalArgumentException("Unsupported granularity");
        }
        // H2's date_trunc('week', ...) follows the JVM locale, unlike PostgreSQL.
        // ISO day-of-week makes Monday explicit on both databases.
        String bucket = granularity.equals("week")
                ? "date_trunc('day',o.date_creation) - (extract(isodow from o.date_creation) - 1) * INTERVAL '1' DAY"
                : "date_trunc('" + granularity + "',o.date_creation)";
        return jdbc.query("SELECT " + bucket + " bucket, "
                + "COALESCE(SUM(o.total_ht),0) ht, COALESCE(SUM(o.total_ttc),0) ttc, COUNT(*) orders "
                + "FROM commande o WHERE " + PAID + " AND " + PERIOD + " GROUP BY 1 ORDER BY 1",
                (rs, n) -> new Bucket(rs.getObject("bucket", LocalDateTime.class).toLocalDate(),
                        rs.getBigDecimal("ht"), rs.getBigDecimal("ttc"), rs.getLong("orders")), bounds(from, to));
    }

    public List<VariantSales> variantSales(Instant from, Instant to) {
        return jdbc.query("""
                SELECT i.variante_id, SUM(CAST(i.quantite AS BIGINT)) quantity,
                       COALESCE(SUM(i.prix_unitaire_ht*i.quantite),0) revenue
                FROM ligne_commande i JOIN commande o ON o.id=i.commande_id WHERE
                """ + PAID + " AND " + PERIOD + " GROUP BY i.variante_id ORDER BY i.variante_id",
                (rs, n) -> new VariantSales(rs.getLong("variante_id"), rs.getLong("quantity"),
                        rs.getBigDecimal("revenue")), bounds(from, to));
    }

    private Object[] bounds(Instant from, Instant to) {
        return new Object[]{LocalDateTime.ofInstant(from, ZoneOffset.UTC), LocalDateTime.ofInstant(to, ZoneOffset.UTC)};
    }
}
