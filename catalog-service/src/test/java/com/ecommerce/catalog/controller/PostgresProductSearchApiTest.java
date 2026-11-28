package com.ecommerce.catalog.controller;

import com.ecommerce.catalog.support.DockerRequiredInCi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Replays every search/facet regression on PostgreSQL as well as testing real pg_trgm. */
@ExtendWith(DockerRequiredInCi.class)
class PostgresProductSearchApiTest extends ProductSearchApiTest {
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        POSTGRES.start();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Test void migratesTrigramExtensionAndIndex() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_extension WHERE extname='pg_trgm'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT indexdef FROM pg_indexes WHERE indexname='idx_produit_title_trgm'", String.class))
                .contains("gin", "lower", "gin_trgm_ops");
    }

    @Test void suggestsTyposWithRelevanceAndLimit() throws Exception {
        mvc.perform(get(PRODUCTS + "/autocomplete").param("q", "logitec").param("limit", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].brand.slug", everyItem(is("logitech"))));
        mvc.perform(get(PRODUCTS + "/autocomplete").param("q", "Logitech G915 TKL").param("limit", "1"))
                .andExpect(jsonPath("$[0].slug").value("logitech-g915-tkl"));
        for (String q : new String[]{"zzzzqqqqxxx", "' OR 1=1 --", "%", "_"}) {
            mvc.perform(get(PRODUCTS + "/autocomplete").param("q", q))
                    .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        }
    }

    @Test void suggestionsHideUnpublishedAndMissingVariants() throws Exception {
        mvc.perform(get(PRODUCTS + "/autocomplete").param("q", "logitech"))
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[*].slug", not(hasItem("logitech-mx-keys"))));
        jdbc.update("DELETE FROM variante WHERE produit_id IN (SELECT id FROM produit WHERE slug='logitech-g915-tkl')");
        mvc.perform(get(PRODUCTS + "/autocomplete").param("q", "logitech"))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].slug", not(hasItem("logitech-g915-tkl"))));
    }
}
