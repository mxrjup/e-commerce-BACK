package com.ecommerce.catalog.controller;

import com.ecommerce.catalog.demo.DemoCatalogLoader;
import com.ecommerce.catalog.support.CatalogTestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:catalogsearch;MODE=PostgreSQL;"
        + "DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class ProductSearchApiTest {
    protected static final String PRODUCTS = "/api/catalog/products";
    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired private DemoCatalogLoader loader;

    @BeforeEach void load() { loader.run(null); }
    @AfterEach void clear() { CatalogTestData.clear(jdbc); }

    @Test void combinesTextHierarchyBrandUniversePriceAndAttributes() throws Exception {
        mvc.perform(get(PRODUCTS).param("q", "  g915  ").param("category", "peripheriques")
                .param("brand", "logitech").param("universe", "gaming")
                .param("minPrice", "200.11").param("maxPrice", "200.11")
                .param("attribute", "Couleur:Noir", "Layout clavier:AZERTY")
                .param("specification", "Garantie:2 ans", "Connectique:Lightspeed, Bluetooth, USB"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].slug").value("logitech-g915-tkl"))
                .andExpect(jsonPath("$.content[0].priceInclTax").value(200.11));
        mvc.perform(get(PRODUCTS).param("q", "g915").param("minPrice", "200.12"))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get(PRODUCTS).param("q", "g915").param("maxPrice", "200.10"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test void usesTheProductsVatRateAndRoundsBeforeFiltering() throws Exception {
        jdbc.update("UPDATE produit SET tva_id=(SELECT id FROM tva WHERE taux=5.50) WHERE slug='logitech-g915-tkl'");
        mvc.perform(get(PRODUCTS).param("q", "g915").param("minPrice", "175.93").param("maxPrice", "175.93"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].priceInclTax").value(175.93));
        mvc.perform(get(PRODUCTS + "/facets").param("q", "g915"))
                .andExpect(jsonPath("$.price.min").value(175.93));
    }

    @Test void treatsCommasAsPartOfTechnicalValues() throws Exception {
        mvc.perform(get(PRODUCTS).param("specification", "Connectique:Lightspeed, Bluetooth, USB"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].slug").value("logitech-g915-tkl"));
    }

    @Test void requiresAttributesOnTheSameVariant() throws Exception {
        // Add QWERTY only to the white G915; Noir+QWERTY must not match.
        jdbc.update("INSERT INTO variante_valeur(variante_id,valeur_id) SELECT v.id,a.id FROM variante v CROSS JOIN valeur_attribut a WHERE v.sku='LOGI-G915TKL-AZ-BLA' AND a.valeur='QWERTY'");
        mvc.perform(get(PRODUCTS).param("q", "g915").param("attribute", "Couleur:Noir", "Layout clavier:QWERTY"))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get(PRODUCTS).param("q", "g915").param("attribute", "Couleur:Blanc", "Layout clavier:QWERTY"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test void paginatesWithoutDuplicatesAndUsesOrWithinAnAttribute() throws Exception {
        mvc.perform(get(PRODUCTS).param("brand", "logitech").param("q", "LOGITECH")
                .param("attribute", "Couleur:Noir", "Couleur:Blanc").param("size", "1").param("sort", "name"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(4))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].slug").value("logitech-g502-x"));
        mvc.perform(get(PRODUCTS).param("brand", "logitech").param("q", "LOGITECH")
                .param("attribute", "Couleur:Noir", "Couleur:Blanc").param("size", "1")
                .param("sort", "name").param("page", "1"))
                .andExpect(jsonPath("$.content[0].slug").value("logitech-g915-tkl"));
    }

    @Test void computesFacetsOverAllMatchingProducts() throws Exception {
        mvc.perform(get(PRODUCTS + "/facets").param("q", "g915"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brands[0].slug").value("logitech"))
                .andExpect(jsonPath("$.brands[0].count").value(1))
                .andExpect(jsonPath("$.categories[0].slug").value("claviers"))
                .andExpect(jsonPath("$.universes[0].slug").value("gaming"))
                .andExpect(jsonPath("$.price.min").value(200.11))
                .andExpect(jsonPath("$.price.max").value(200.11))
                .andExpect(jsonPath("$.attributes[?(@.name == 'Couleur')].value", containsInAnyOrder("Noir", "Blanc")))
                .andExpect(jsonPath("$.attributes[*].count", everyItem(is(1))))
                .andExpect(jsonPath("$.specifications[?(@.name == 'Garantie')].value", contains("2 ans")));
        mvc.perform(get(PRODUCTS + "/facets").param("category", "unknown"))
                .andExpect(jsonPath("$.brands", hasSize(0))).andExpect(jsonPath("$.price.min").value(nullValue()));
        mvc.perform(get(PRODUCTS + "/facets").param("brand", "logitech").param("minPrice", "200")
                .param("attribute", "Couleur:Blanc"))
                .andExpect(jsonPath("$.brands[0].count").value(1));
    }

    @Test void excludesUnpublishedAndProductsWithoutVariants() throws Exception {
        jdbc.update("DELETE FROM variante WHERE produit_id IN (SELECT id FROM produit WHERE slug='logitech-g915-tkl')");
        mvc.perform(get(PRODUCTS).param("q", "g915")).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get(PRODUCTS + "/facets").param("q", "g915")).andExpect(jsonPath("$.brands", hasSize(0)));
        mvc.perform(get(PRODUCTS).param("q", "vengeance")).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get(PRODUCTS + "/facets").param("q", "vengeance")).andExpect(jsonPath("$.attributes", hasSize(0)));
    }

    @Test void handlesEmptyUnknownAndLiteralSearches() throws Exception {
        mvc.perform(get(PRODUCTS).param("q", "  ")).andExpect(jsonPath("$.totalElements").value(17));
        for (String query : new String[]{"not-a-product", "%", "_", "' OR 1=1 --"}) {
            mvc.perform(get(PRODUCTS).param("q", query)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0));
        }
        mvc.perform(get(PRODUCTS + "/autocomplete").param("q", " "))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test void rejectsInvalidSearchParameters() throws Exception {
        for (String endpoint : new String[]{PRODUCTS, PRODUCTS + "/facets"}) {
            mvc.perform(get(endpoint).param("minPrice", "-1")).andExpect(status().isBadRequest());
            mvc.perform(get(endpoint).param("minPrice", "10").param("maxPrice", "5")).andExpect(status().isBadRequest());
            mvc.perform(get(endpoint).param("maxPrice", "abc")).andExpect(status().isBadRequest());
            mvc.perform(get(endpoint).param("q", "x".repeat(151))).andExpect(status().isBadRequest());
            mvc.perform(get(endpoint).param("attribute", "invalid")).andExpect(status().isBadRequest());
            mvc.perform(get(endpoint).param("specification", "Garantie: ")).andExpect(status().isBadRequest());
        }
        mvc.perform(get(PRODUCTS + "/autocomplete").param("limit", "0")).andExpect(status().isBadRequest());
        mvc.perform(get(PRODUCTS + "/autocomplete").param("limit", "21")).andExpect(status().isBadRequest());
        mvc.perform(get(PRODUCTS + "/autocomplete").param("q", "x".repeat(151))).andExpect(status().isBadRequest());
    }
}
