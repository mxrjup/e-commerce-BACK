package com.ecommerce.catalog.controller;

import com.ecommerce.catalog.demo.DemoCatalogLoader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
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
    @Autowired private ObjectMapper mapper;
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

    @Test void eachReferenceAndPriceFacetIgnoresOnlyItsOwnSelection() throws Exception {
        JsonNode baseline = facets();
        for (String[] selection : new String[][]{{"category", "claviers", "categories"},
                {"brand", "logitech", "brands"}, {"universe", "gaming", "universes"}}) {
            JsonNode selected = facets(selection[0], selection[1]);
            assertThat(selected.get(selection[2])).isEqualTo(baseline.get(selection[2]));
        }
        assertThat(facets("minPrice", "200", "maxPrice", "210").get("price"))
                .isEqualTo(baseline.get("price"));
        assertThat(facets("brand", "logitech").get("categories"))
                .isNotEqualTo(baseline.get("categories"));
        // Category options retain the brand filter even while ignoring the selected category.
        assertThat(facets("brand", "logitech", "category", "claviers").get("categories"))
                .isEqualTo(facets("brand", "logitech").get("categories"));
        assertThat(facets("q", "g915", "minPrice", "999").get("price").get("min").decimalValue())
                .isEqualByComparingTo("200.11");
    }

    @Test void colorCountsDoNotShrinkWhenAnotherColorIsSelected() throws Exception {
        mvc.perform(get(PRODUCTS + "/facets").param("attribute", "Couleur:Noir"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attributes[?(@.name == 'Couleur' && @.value == 'Blanc')].count", contains(4)));
        JsonNode baseline = facets("brand", "logitech");
        JsonNode selected = facets("brand", "logitech", "attribute", "Couleur:Noir");
        assertThat(values(selected, "attributes", "Couleur"))
                .isEqualTo(values(baseline, "attributes", "Couleur"));
        assertThat(values(facets("attribute", "Couleur:Noir", "attribute", "Couleur:Blanc"), "attributes", "Couleur"))
                .isEqualTo(values(facets(), "attributes", "Couleur"));
    }

    @Test void attributeFacetsKeepOtherSelectionsOnTheSameVariant() throws Exception {
        // White is the only QWERTY variant. A product-level match alone would wrongly expose Noir.
        jdbc.update("INSERT INTO variante_valeur(variante_id,valeur_id) SELECT v.id,a.id FROM variante v CROSS JOIN valeur_attribut a WHERE v.sku='LOGI-G915TKL-AZ-BLA' AND a.valeur='QWERTY'");
        mvc.perform(get(PRODUCTS + "/facets").param("q", "g915")
                .param("attribute", "Couleur:Noir", "Layout clavier:QWERTY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attributes[?(@.name == 'Couleur')].value", contains("Blanc")))
                .andExpect(jsonPath("$.attributes[?(@.name == 'Layout clavier')].value", contains("AZERTY")));
    }

    @Test void specificationFacetsRemoveOnlyTheirOwnKey() throws Exception {
        JsonNode baseline = facets("specification", "Connectique:Lightspeed, Bluetooth, USB");
        JsonNode selected = facets("specification", "Connectique:Lightspeed, Bluetooth, USB",
                "specification", "Garantie:inconnue");
        assertThat(values(selected, "specifications", "Garantie"))
                .isEqualTo(values(baseline, "specifications", "Garantie"));
        assertThat(values(selected, "specifications", "Garantie")).hasSize(1);
        assertThat(selected.get("brands").isEmpty()).isTrue();
        assertThat(values(selected, "specifications", "Connectique")).isEmpty();
    }

    @Test void textSearchIncludesCategoryBrandAndFrenchAccents() throws Exception {
        mvc.perform(get(PRODUCTS).param("q", "clavier"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
        JsonNode accented = mapper.readTree(mvc.perform(get(PRODUCTS).param("q", "ÉCRAN"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode plain = mapper.readTree(mvc.perform(get(PRODUCTS).param("q", "ecran"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(plain).isEqualTo(accented);
        assertThat(plain.get("totalElements").asInt()).isPositive();
        jdbc.update("UPDATE marque SET nom='Marque éphémère' WHERE slug='logitech'");
        mvc.perform(get(PRODUCTS).param("q", "ephemere"))
                .andExpect(jsonPath("$.totalElements").value(4));
        mvc.perform(get(PRODUCTS + "/facets").param("q", "ephemere"))
                .andExpect(jsonPath("$.brands[0].count").value(4));
    }

    @Test void literalAutocompleteWorksOnBothDatabases() throws Exception {
        mvc.perform(get(PRODUCTS + "/autocomplete").param("q", "logitech").param("limit", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].brand.slug", everyItem(is("logitech"))));
        mvc.perform(get(PRODUCTS + "/autocomplete").param("q", "%"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    private JsonNode facets(String... parameters) throws Exception {
        var request = get(PRODUCTS + "/facets");
        for (int i = 0; i < parameters.length; i += 2) request.param(parameters[i], parameters[i + 1]);
        return mapper.readTree(mvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private java.util.List<JsonNode> values(JsonNode facets, String group, String name) {
        java.util.List<JsonNode> result = new java.util.ArrayList<>();
        facets.get(group).forEach(value -> {
            if (value.get("name").asText().equals(name)) result.add(value);
        });
        return result;
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
