package com.ecommerce.order.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatsRepositoryTest {
    private JdbcTemplate jdbc;
    private OrderStatsRepository repository;
    private DriverManagerDataSource postgresAdmin;
    private String schema;
    private final Instant from = Instant.parse("2026-01-01T00:00:00Z");
    private final Instant to = Instant.parse("2026-03-01T00:00:00Z");

    @BeforeEach
    void setup() {
        String postgresUrl = System.getProperty("stats.test.postgres-url");
        DriverManagerDataSource ds;
        if (postgresUrl == null) {
            ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        } else {
            postgresAdmin = new DriverManagerDataSource(postgresUrl, "postgres", "");
            schema = "stats_test_" + UUID.randomUUID().toString().replace("-", "");
            new JdbcTemplate(postgresAdmin).execute("CREATE SCHEMA " + schema);
            ds = new DriverManagerDataSource(postgresUrl + (postgresUrl.contains("?") ? "&" : "?") + "currentSchema=" + schema, "postgres", "");
        }
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1__init_order_schema.sql"),
                new ClassPathResource("db/migration/V2__freeze_purchase_price.sql"),
                new ClassPathResource("db/migration/V5__order_stats_indexes.sql")).execute(ds);
        jdbc = new JdbcTemplate(ds);
        repository = new OrderStatsRepository(jdbc);
    }

    @AfterEach
    void dropIsolatedSchema() {
        if (postgresAdmin != null) new JdbcTemplate(postgresAdmin).execute("DROP SCHEMA " + schema + " CASCADE");
    }

    @Test
    void aggregatesPaidOrdersOnceAndExcludesUnpaidCancelledAndOutsideBounds() {
        order(1, "IN_PREPARATION", "2026-01-01", "100", "125");
        order(2, "SHIPPED", "2026-01-04", "50", "60");
        order(3, "DELIVERED", "2026-02-01", "20", "24");
        order(4, "NEW", "2026-01-01", "900", "999");
        order(5, "CANCELLED", "2026-01-01", "900", "999");
        order(6, "DELIVERED", "2026-03-01", "900", "999");
        order(7, "DELIVERED", "2025-12-31", "900", "999");
        item(1, 11, "40", "25", 2);
        item(1, 12, "20", "10", 1);
        item(2, 11, "50", "20", 1);
        item(3, 13, "20", "25", 1);
        item(4, 11, "900", "0", 1);
        item(5, 11, "900", "0", 1);
        var totals = repository.totals(from, to);
        assertThat(totals.revenueExclTax()).isEqualByComparingTo("170");
        assertThat(totals.revenueInclTax()).isEqualByComparingTo("209");
        assertThat(totals.orderCount()).isEqualTo(3);
        assertThat(totals.pendingOrders()).isEqualTo(1);
        assertThat(totals.missingTotals()).isZero();
        assertThat(repository.margin(from, to).knownMargin()).isEqualByComparingTo("65");
        assertThat(repository.variantSales(from, to)).hasSize(3);
        var variant = repository.variantSales(from, to).getFirst();
        assertThat(variant.variantId()).isEqualTo(11);
        assertThat(variant.quantity()).isEqualTo(3);
        assertThat(variant.revenueExclTax()).isEqualByComparingTo("130");
        assertThat(repository.curve(from, to, "day")).hasSize(3);
        var weeks = repository.curve(from, to, "week");
        assertThat(weeks).hasSize(2);
        assertThat(weeks.getFirst().date()).hasToString("2025-12-29");
        assertThat(weeks.getFirst().revenueInclTax()).isEqualByComparingTo("185");
        var months = repository.curve(from, to, "month");
        assertThat(months).hasSize(2);
        assertThat(months.getFirst().date()).hasToString("2026-01-01");
        assertThat(months.getFirst().orderCount()).isEqualTo(2);
    }

    @Test
    void weeklyBucketsKeepSundayWithPreviousMondayAcrossYearBoundary() {
        order(1, "DELIVERED", "2026-01-01", "10", "12");
        order(2, "DELIVERED", "2026-01-04", "20", "24");
        order(3, "DELIVERED", "2026-01-05", "30", "36");
        var weeks = repository.curve(from, to, "week");
        assertThat(weeks).extracting(bucket -> bucket.date().toString())
                .containsExactly("2025-12-29", "2026-01-05");
        assertThat(weeks.getFirst().orderCount()).isEqualTo(2);
        assertThat(weeks.getFirst().revenueInclTax()).isEqualByComparingTo("36");
        assertThat(weeks.getLast().orderCount()).isOne();
    }

    @Test
    void distinguishesUnknownCostsAndNullTotalsFromZero() {
        order(1, "DELIVERED", "2026-01-01", null, null);
        item(1, 11, "10", null, 2);
        item(1, 12, "10", "0", 1);
        var totals = repository.totals(from, to);
        assertThat(totals.revenueExclTax()).isEqualByComparingTo("0");
        assertThat(totals.revenueInclTax()).isEqualByComparingTo("0");
        assertThat(totals.orderCount()).isOne();
        assertThat(totals.missingTotals()).isOne();
        var margin = repository.margin(from, to);
        assertThat(margin.knownMargin()).isEqualByComparingTo("10");
        assertThat(margin.missingPurchasePrices()).isOne();
    }

    @Test
    void returnsZeroAggregatesForEmptyPeriod() {
        assertThat(repository.totals(from, to).orderCount()).isZero();
        assertThat(repository.totals(from, to).revenueInclTax()).isEqualByComparingTo("0");
        assertThat(repository.margin(from, to).knownMargin()).isEqualByComparingTo("0");
        assertThat(repository.margin(from, to).missingPurchasePrices()).isZero();
        assertThat(repository.curve(from, to, "day")).isEmpty();
        assertThat(repository.variantSales(from, to)).isEmpty();
    }

    private void order(long id, String status, String date, String ht, String ttc) {
        jdbc.update("INSERT INTO commande(id,numero,utilisateur_id,statut,date_creation,total_ht,total_ttc) VALUES(?,?,1,?,?,?,?)",
                id, "CMD-" + id, status, java.time.LocalDate.parse(date).atStartOfDay(),
                ht == null ? null : new java.math.BigDecimal(ht), ttc == null ? null : new java.math.BigDecimal(ttc));
    }
    private void item(long order, long variant, String price, String cost, int quantity) {
        jdbc.update("INSERT INTO ligne_commande(commande_id,variante_id,designation,prix_unitaire_ht,taux_tva,prix_achat_ht,quantite) VALUES(?,?,'Product',?,20,?,?)",
                order, variant, new java.math.BigDecimal(price), cost == null ? null : new java.math.BigDecimal(cost), quantity);
    }
}
