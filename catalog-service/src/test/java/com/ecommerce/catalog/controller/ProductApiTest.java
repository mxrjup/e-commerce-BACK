package com.ecommerce.catalog.controller;

import com.ecommerce.catalog.demo.DemoCatalogLoader;
import com.ecommerce.catalog.support.CatalogTestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.in;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The storefront product API (C02) over the demonstration catalogue: 19
 * products, of which 17 are published. Expected prices follow the rounding
 * of order-service.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:catalogpublic;MODE=PostgreSQL;"
        + "DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class ProductApiTest {

    private static final String PRODUCTS = "/api/catalog/products";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DemoCatalogLoader loader;

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

    @Nested
    class Listing {

        @Test
        void shouldListOnlyPublishedProductsNewestFirst() throws Exception {
            mockMvc.perform(get(PRODUCTS))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(17))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(20))
                    .andExpect(jsonPath("$.totalPages").value(1))
                    .andExpect(jsonPath("$.content", hasSize(17)))
                    .andExpect(jsonPath("$.content[0].slug").value("crucial-pro-ddr5-6000"))
                    .andExpect(jsonPath("$.content[*].slug", not(hasItem("corsair-vengeance-rgb-ddr5"))))
                    .andExpect(jsonPath("$.content[*].slug", not(hasItem("logitech-mx-keys"))));
        }

        @Test
        void shouldShowAProductCardWithItsCheapestPriceIncludingVat() throws Exception {
            mockMvc.perform(get(PRODUCTS).param("brand", "logitech").param("sort", "name"))
                    .andExpect(jsonPath("$.content[*].slug").value(contains(
                            "logitech-g502-x", "logitech-g915-tkl", "logitech-mx-keys-s", "logitech-mx-master-3s")))
                    // G915 noir en promo : 166,66 + 0,10 d'ecotaxe, TVA 20 %.
                    .andExpect(jsonPath("$.content[1].title").value("Logitech G915 TKL"))
                    .andExpect(jsonPath("$.content[1].brand.name").value("Logitech"))
                    .andExpect(jsonPath("$.content[1].category.slug").value("claviers"))
                    .andExpect(jsonPath("$.content[1].priceInclTax").value(200.11))
                    .andExpect(jsonPath("$.content[1].regularPriceInclTax").value(230.11))
                    .andExpect(jsonPath("$.content[1].available").value(true))
                    .andExpect(jsonPath("$.content[1].variantCount").value(2))
                    .andExpect(jsonPath("$.content[2].regularPriceInclTax").value(nullValue()))
                    .andExpect(jsonPath("$.content[0]", not(hasKey("purchasePriceExclTax"))));
        }

        @Test
        void shouldIncludeTheSubCategoriesOfACategory() throws Exception {
            mockMvc.perform(get(PRODUCTS).param("category", "peripheriques"))
                    .andExpect(jsonPath("$.totalElements").value(8))
                    .andExpect(jsonPath("$.content[*].category.slug",
                            everyItem(in(List.of("claviers", "souris", "casques")))));

            mockMvc.perform(get(PRODUCTS).param("category", "claviers"))
                    .andExpect(jsonPath("$.totalElements").value(3));
        }

        @Test
        void shouldCombineTheFilters() throws Exception {
            mockMvc.perform(get(PRODUCTS).param("universe", "gaming"))
                    .andExpect(jsonPath("$.totalElements").value(11));

            mockMvc.perform(get(PRODUCTS).param("universe", "gaming").param("brand", "logitech"))
                    .andExpect(jsonPath("$.content[*].slug", containsInAnyOrder("logitech-g915-tkl", "logitech-g502-x")));
        }

        @Test
        void shouldSortByPriceIncludingVat() throws Exception {
            mockMvc.perform(get(PRODUCTS).param("sort", "price-asc").param("size", "3"))
                    .andExpect(jsonPath("$.content[*].slug").value(contains(
                            "logitech-g502-x", "logitech-mx-master-3s", "crucial-pro-ddr5-6000")))
                    .andExpect(jsonPath("$.content[0].priceInclTax").value(70.06))
                    .andExpect(jsonPath("$.content[0].regularPriceInclTax").value(90.05));

            mockMvc.perform(get(PRODUCTS).param("sort", "price-desc").param("size", "1"))
                    .andExpect(jsonPath("$.content[0].slug").value("asus-rog-zephyrus-g14"))
                    .andExpect(jsonPath("$.content[0].priceInclTax").value(2200.20));
        }

        @Test
        void shouldSortByNameWhateverTheCase() throws Exception {
            mockMvc.perform(get(PRODUCTS).param("sort", "name").param("size", "2"))
                    .andExpect(jsonPath("$.content[*].title").value(contains(
                            "Apple MacBook Air 13\" M3", "ASUS ROG Strix GeForce RTX 4070 Ti Super")));
        }

        @Test
        void shouldPaginate() throws Exception {
            mockMvc.perform(get(PRODUCTS).param("sort", "price-asc").param("size", "5").param("page", "3"))
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath("$.page").value(3))
                    .andExpect(jsonPath("$.totalPages").value(4))
                    .andExpect(jsonPath("$.content[1].slug").value("asus-rog-zephyrus-g14"));
        }

        @Test
        void shouldTellAProductOutOfStock() throws Exception {
            mockMvc.perform(get(PRODUCTS).param("brand", "corsair"))
                    .andExpect(jsonPath("$.content[?(@.slug == 'corsair-k70-rgb-pro')].available").value(contains(false)));
        }

        @Test
        void shouldAnswerAnEmptyPageForUnknownFilters() throws Exception {
            mockMvc.perform(get(PRODUCTS).param("category", "inconnue"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0))
                    .andExpect(jsonPath("$.content", hasSize(0)));
            mockMvc.perform(get(PRODUCTS).param("brand", "inconnue"))
                    .andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        void shouldRejectInvalidPagingOrSort() throws Exception {
            mockMvc.perform(get(PRODUCTS).param("size", "0")).andExpect(status().isBadRequest());
            mockMvc.perform(get(PRODUCTS).param("size", "51")).andExpect(status().isBadRequest());
            mockMvc.perform(get(PRODUCTS).param("page", "-1")).andExpect(status().isBadRequest());
            // page * size depasserait un int : refuse en 400, pas un 500.
            mockMvc.perform(get(PRODUCTS).param("page", "50000000").param("size", "50"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("page is too large"));
            mockMvc.perform(get(PRODUCTS).param("sort", "popular"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("sort must be one of newest, price-asc, price-desc, name"));
        }
    }

    @Nested
    class Detail {

        @Test
        void shouldServeAProductPage() throws Exception {
            mockMvc.perform(get(PRODUCTS + "/{slug}", "logitech-g915-tkl"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("Logitech G915 TKL"))
                    .andExpect(jsonPath("$.description").value(
                            "<p>Clavier mécanique gaming sans pavé numérique, switches low profile.</p>"))
                    .andExpect(jsonPath("$.brand.slug").value("logitech"))
                    .andExpect(jsonPath("$.category.name").value("Claviers"))
                    .andExpect(jsonPath("$.universes[*].slug").value(contains("gaming")))
                    .andExpect(jsonPath("$.vatRate").value(20.00))
                    .andExpect(jsonPath("$.specifications[*].key").value(contains("Switches", "Connectique", "Garantie")))
                    .andExpect(jsonPath("$.attributes[0].name").value("Couleur"))
                    .andExpect(jsonPath("$.attributes[0].values").value(contains("Noir", "Blanc")))
                    .andExpect(jsonPath("$.attributes[1].name").value("Layout clavier"))
                    .andExpect(jsonPath("$.attributes[1].values").value(contains("AZERTY")));
        }

        @Test
        void shouldGiveEachVariantItsPriceAndAvailabilityButNotItsStock() throws Exception {
            mockMvc.perform(get(PRODUCTS + "/{slug}", "logitech-g915-tkl"))
                    .andExpect(jsonPath("$.variants", hasSize(2)))
                    .andExpect(jsonPath("$.variants[0].sku").value("LOGI-G915TKL-AZ-NOI"))
                    .andExpect(jsonPath("$.variants[0].values.Couleur").value("Noir"))
                    .andExpect(jsonPath("$.variants[0].values['Layout clavier']").value("AZERTY"))
                    .andExpect(jsonPath("$.variants[0].priceInclTax").value(200.11))
                    .andExpect(jsonPath("$.variants[0].regularPriceInclTax").value(230.11))
                    .andExpect(jsonPath("$.variants[0].ecoTaxInclTax").value(0.12))
                    .andExpect(jsonPath("$.variants[0].available").value(true))
                    .andExpect(jsonPath("$.variants[0].lowStock").value(false))
                    // Blanc : 2 en stock pour un seuil de 3.
                    .andExpect(jsonPath("$.variants[1].priceInclTax").value(230.11))
                    .andExpect(jsonPath("$.variants[1].regularPriceInclTax").value(nullValue()))
                    .andExpect(jsonPath("$.variants[1].lowStock").value(true))
                    .andExpect(jsonPath("$.variants[0]", not(hasKey("stock"))))
                    .andExpect(jsonPath("$.variants[0]", not(hasKey("purchasePriceExclTax"))));
        }

        @Test
        void shouldNotFlagALowStockOnAVariantOutOfStock() throws Exception {
            mockMvc.perform(get(PRODUCTS + "/{slug}", "corsair-k70-rgb-pro"))
                    .andExpect(jsonPath("$.variants[0].available").value(false))
                    .andExpect(jsonPath("$.variants[0].lowStock").value(false));
        }

        @Test
        void shouldHideDraftsArchivedAndUnknownProducts() throws Exception {
            mockMvc.perform(get(PRODUCTS + "/{slug}", "corsair-vengeance-rgb-ddr5")).andExpect(status().isNotFound());
            mockMvc.perform(get(PRODUCTS + "/{slug}", "logitech-mx-keys")).andExpect(status().isNotFound());
            mockMvc.perform(get(PRODUCTS + "/{slug}", "inconnu"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }
    }
}
