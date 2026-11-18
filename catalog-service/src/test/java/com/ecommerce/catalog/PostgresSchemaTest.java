package com.ecommerce.catalog;

import com.ecommerce.catalog.repository.ProductRepository;
import com.ecommerce.catalog.service.StockService;
import com.ecommerce.catalog.support.DockerRequiredInCi;
import com.ecommerce.common.exception.ConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The migrations, the schema validation and the demonstration data on the
 * PostgreSQL the service runs on. The other tests use H2, which accepts
 * column types that Hibernate refuses on PostgreSQL: without this test, the
 * build could pass while the service fails to start.
 */
@ExtendWith(DockerRequiredInCi.class)
@SpringBootTest
@ActiveProfiles("demo")
class PostgresSchemaTest {

    /** Same major version as the Docker Compose stack. Stopped by Testcontainers when the JVM exits. */
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
    private ProductRepository productRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StockService stockService;

    @Test
    void shouldMigrateValidateAndLoadTheDemoCatalogue() {
        // Le contexte a demarre : Flyway a joue V1 a V4, et Hibernate a valide le schema.
        assertThat(jdbcTemplate.queryForObject("SELECT MAX(version) FROM flyway_schema_history", String.class))
                .isEqualTo("4");
        assertThat(productRepository.count()).isEqualTo(19);
    }

    @Test
    void shouldRefuseOverselling() {
        Long id = jdbcTemplate.queryForObject("SELECT id FROM variante WHERE sku = 'SAMS-ODG7-27'", Long.class);

        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE variante SET stock = stock - 6 WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT stock FROM variante WHERE id = ?", Integer.class, id))
                .isEqualTo(5);
    }

    /** The conditional UPDATE of C07 on PostgreSQL's real row locks: 20 buyers, 7 units, 7 sales. */
    @Test
    void shouldNeverSellMoreThanTheStockUnderConcurrency() throws Exception {
        Long id = jdbcTemplate.queryForObject("SELECT id FROM variante WHERE sku = 'DELL-U2723QE'", Long.class);
        int buyers = 20;
        ExecutorService pool = Executors.newFixedThreadPool(buyers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < buyers; i++) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    stockService.decrement(Map.of(id, 1));
                    return true;
                } catch (ConflictException e) {
                    return false;
                }
            }));
        }
        start.countDown();
        int sold = 0;
        for (Future<Boolean> result : results) {
            if (result.get(30, TimeUnit.SECONDS)) {
                sold++;
            }
        }
        pool.shutdown();

        assertThat(sold).isEqualTo(7);
        assertThat(jdbcTemplate.queryForObject("SELECT stock FROM variante WHERE id = ?", Integer.class, id)).isZero();
    }

    @Test
    void shouldSearchTheInventory() {
        assertThat(stockService.inventory("g915", false, 0, 20).content())
                .extracting(line -> line.sku())
                .containsExactlyInAnyOrder("LOGI-G915TKL-AZ-NOI", "LOGI-G915TKL-AZ-BLA");
        assertThat(stockService.inventory("%", false, 0, 20).totalElements()).isZero();
        assertThat(stockService.inventory(null, true, 0, 20).content())
                .allMatch(line -> line.lowStock());
    }

    /** C21 on PostgreSQL's row locks: 10 paid orders for 2 units take 2, report 8 short, never go below zero. */
    @Test
    void shouldTakeConcurrentPaidOrdersWithoutGoingBelowZero() throws Exception {
        Long id = jdbcTemplate.queryForObject("SELECT id FROM variante WHERE sku = 'LG-27GR95QE'", Long.class);
        int orders = 10;
        ExecutorService pool = Executors.newFixedThreadPool(orders);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<List<StockService.Shortage>>> results = new ArrayList<>();
        for (int i = 0; i < orders; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return stockService.takePaidOrder(Map.of(id, 1));
            }));
        }
        start.countDown();
        int short_ = 0;
        for (Future<List<StockService.Shortage>> result : results) {
            short_ += result.get(30, TimeUnit.SECONDS).size();
        }
        pool.shutdown();

        assertThat(short_).isEqualTo(8);
        assertThat(jdbcTemplate.queryForObject("SELECT stock FROM variante WHERE id = ?", Integer.class, id)).isZero();
    }
}
