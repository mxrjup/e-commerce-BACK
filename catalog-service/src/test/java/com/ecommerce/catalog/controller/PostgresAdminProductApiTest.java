package com.ecommerce.catalog.controller;

import com.ecommerce.catalog.dto.AdminVariantRequest;
import com.ecommerce.catalog.service.AdminVariantService;
import com.ecommerce.catalog.support.DockerRequiredInCi;
import com.ecommerce.common.exception.ConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every back-office product test again on PostgreSQL 16, whose sorting,
 * LIKE and constraints are the ones of production, plus the row lock that
 * keeps two identical variants out.
 */
@ExtendWith(DockerRequiredInCi.class)
class PostgresAdminProductApiTest extends AdminProductApiTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        POSTGRES.start();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private AdminVariantService variantService;

    /** 8 back-office users add the same variant at once: the product lock lets exactly one through. */
    @Test
    void shouldCreateOnlyOneOfConcurrentIdenticalVariants() throws Exception {
        Long productId = jdbcTemplate.queryForObject(
                "SELECT id FROM produit WHERE slug = 'corsair-vengeance-rgb-ddr5'", Long.class);
        Long value = jdbcTemplate.queryForObject(
                "SELECT id FROM valeur_attribut WHERE valeur = '64 Go (2 x 32 Go)'", Long.class);
        int users = 8;
        ExecutorService pool = Executors.newFixedThreadPool(users);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>();
        for (int i = 0; i < users; i++) {
            String sku = "CORS-VENG-DDR5-64-" + i;
            results.add(pool.submit(() -> {
                start.await();
                return variantService.create(productId, new AdminVariantRequest(sku, new BigDecimal("150"),
                        new BigDecimal("229.90"), null, null, new BigDecimal("0.1"), List.of(value), 5, 1));
            }));
        }
        start.countDown();
        int created = 0;
        int refused = 0;
        for (Future<?> result : results) {
            try {
                result.get(30, TimeUnit.SECONDS);
                created++;
            } catch (ExecutionException ex) {
                assertThat(ex.getCause()).isInstanceOf(ConflictException.class);
                refused++;
            }
        }
        pool.shutdown();

        assertThat(created).isEqualTo(1);
        assertThat(refused).isEqualTo(users - 1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM variante WHERE produit_id = ?", Integer.class, productId)).isEqualTo(2);
    }
}
