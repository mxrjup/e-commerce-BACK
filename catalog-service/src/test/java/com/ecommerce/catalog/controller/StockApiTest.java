package com.ecommerce.catalog.controller;

import com.ecommerce.catalog.demo.DemoCatalogLoader;
import com.ecommerce.catalog.service.StockService;
import com.ecommerce.catalog.support.CatalogTestData;
import com.ecommerce.common.exception.ConflictException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

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
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The stock (C07) over the demonstration catalogue: 30 variants, 8 of them
 * at or below their alert threshold.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:catalogstock;MODE=PostgreSQL;"
        + "DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class StockApiTest {

    private static final String ADMIN = "/api/admin/catalog";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DemoCatalogLoader loader;

    @Autowired
    private StockService stockService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void loadIfEmpty() {
        loader.run(null);
    }

    @AfterEach
    void tearDown() {
        CatalogTestData.clear(jdbcTemplate);
    }

    @Test
    void shouldListEveryVariantWithItsExactStock() throws Exception {
        mockMvc.perform(get(ADMIN + "/inventory").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(30))
                .andExpect(jsonPath("$.content", hasSize(5)))
                .andExpect(jsonPath("$.content[0].designation").value("Apple MacBook Air 13\" M3 - Gris sidéral, 16 Go / 512 Go"))
                .andExpect(jsonPath("$.content[0].sku").value("APPL-MBA13M3-16-512-GRI"))
                .andExpect(jsonPath("$.content[0].stock").value(6))
                .andExpect(jsonPath("$.content[0].alertThreshold").value(2))
                .andExpect(jsonPath("$.content[0].lowStock").value(false))
                .andExpect(jsonPath("$.content[0].productStatus").value("PUBLIE"));
    }

    @Test
    void shouldListOnlyTheAlertsWhenAsked() throws Exception {
        mockMvc.perform(get(ADMIN + "/inventory").param("lowStock", "true"))
                .andExpect(jsonPath("$.totalElements").value(8))
                .andExpect(jsonPath("$.content[*].lowStock", everyItem(org.hamcrest.Matchers.is(true))));
    }

    @Test
    void shouldSearchByTitleOrSkuTakingTheTextLiterally() throws Exception {
        mockMvc.perform(get(ADMIN + "/inventory").param("search", "G915"))
                .andExpect(jsonPath("$.content[*].sku", containsInAnyOrder("LOGI-G915TKL-AZ-NOI", "LOGI-G915TKL-AZ-BLA")));
        mockMvc.perform(get(ADMIN + "/inventory").param("search", "logi-mx"))
                .andExpect(jsonPath("$.totalElements").value(6));
        // % et _ sont cherches tels quels, pas comme jokers.
        mockMvc.perform(get(ADMIN + "/inventory").param("search", "%"))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get(ADMIN + "/inventory").param("search", "_"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void shouldAddADeliveryToTheStock() throws Exception {
        long white = idOf("LOGI-G915TKL-AZ-BLA");

        mockMvc.perform(post(ADMIN + "/variants/{id}/stock-adjustments", white)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"delta\": 10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(12))
                .andExpect(jsonPath("$.lowStock").value(false));

        assertThat(stockOf("LOGI-G915TKL-AZ-BLA")).isEqualTo(12);
    }

    @Test
    void shouldRefuseToGoBelowZeroAndKeepTheStock() throws Exception {
        long white = idOf("LOGI-G915TKL-AZ-BLA");

        mockMvc.perform(post(ADMIN + "/variants/{id}/stock-adjustments", white)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"delta\": -5}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Variant " + white
                        + " does not have enough stock to remove 5 units"));

        assertThat(stockOf("LOGI-G915TKL-AZ-BLA")).isEqualTo(2);

        mockMvc.perform(post(ADMIN + "/variants/{id}/stock-adjustments", white)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"delta\": -2}"))
                .andExpect(jsonPath("$.stock").value(0));
    }

    @Test
    void shouldValidateTheAdjustment() throws Exception {
        mockMvc.perform(post(ADMIN + "/variants/{id}/stock-adjustments", 999_999)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"delta\": 1}"))
                .andExpect(status().isNotFound());
        long id = idOf("LOGI-G915TKL-AZ-BLA");
        mockMvc.perform(post(ADMIN + "/variants/{id}/stock-adjustments", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(ADMIN + "/variants/{id}/stock-adjustments", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"delta\": 2000000}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldChangeTheAlertThreshold() throws Exception {
        long black = idOf("LOGI-G915TKL-AZ-NOI");

        mockMvc.perform(put(ADMIN + "/variants/{id}/alert-threshold", black)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"alertThreshold\": 10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(8))
                .andExpect(jsonPath("$.alertThreshold").value(10))
                .andExpect(jsonPath("$.lowStock").value(true));

        mockMvc.perform(put(ADMIN + "/variants/{id}/alert-threshold", black)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"alertThreshold\": -1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldDecrementAnOrderAllOrNothing() {
        long black = idOf("LOGI-G915TKL-AZ-NOI");
        long k70 = idOf("CORS-K70PRO-AZ-NOI");

        assertThatThrownBy(() -> stockService.decrement(Map.of(black, 2, k70, 1)))
                .isInstanceOf(ConflictException.class);
        assertThat(stockOf("LOGI-G915TKL-AZ-NOI")).isEqualTo(8);

        stockService.decrement(Map.of(black, 2));
        assertThat(stockOf("LOGI-G915TKL-AZ-NOI")).isEqualTo(6);
    }

    @Test
    void shouldNeverSellMoreThanTheStockUnderConcurrency() throws Exception {
        long samsung = idOf("SAMS-ODG7-27");
        assertThat(stockOf("SAMS-ODG7-27")).isEqualTo(5);

        int buyers = 20;
        ExecutorService pool = Executors.newFixedThreadPool(buyers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < buyers; i++) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    stockService.decrement(Map.of(samsung, 1));
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

        assertThat(sold).isEqualTo(5);
        assertThat(stockOf("SAMS-ODG7-27")).isZero();
    }

    private long idOf(String sku) {
        return jdbcTemplate.queryForObject("SELECT id FROM variante WHERE sku = ?", Long.class, sku);
    }

    private int stockOf(String sku) {
        return jdbcTemplate.queryForObject("SELECT stock FROM variante WHERE sku = ?", Integer.class, sku);
    }
}
