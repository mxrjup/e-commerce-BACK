package com.ecommerce.catalog.controller;

import com.ecommerce.catalog.demo.DemoCatalogLoader;
import com.ecommerce.catalog.dto.AdminProductRequest;
import com.ecommerce.catalog.dto.AdminVariantRequest;
import com.ecommerce.catalog.entity.ProductStatus;
import com.ecommerce.catalog.repository.ProductRepository;
import com.ecommerce.catalog.service.AdminProductService;
import com.ecommerce.catalog.repository.VariantRepository;
import com.ecommerce.catalog.service.AdminVariantService;
import com.ecommerce.catalog.service.StockService;
import com.ecommerce.catalog.support.CatalogTestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The back-office API of the products and variants (C04), over the
 * demonstration catalogue: 19 products (17 published, a draft, an archived
 * one) and 30 variants.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:catalogadmin;MODE=PostgreSQL;"
        + "DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class AdminProductApiTest {

    private static final String ADMIN = "/api/admin/catalog";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    private DemoCatalogLoader loader;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AdminVariantService variantService;

    @Autowired
    private VariantRepository variantRepository;

    @Autowired
    private AdminProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockService stockService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void loadIfEmpty() {
        loader.run(null);
    }

    @AfterEach
    void tearDown() {
        CatalogTestData.clear(jdbcTemplate);
        jdbcTemplate.update("UPDATE tva SET actif = TRUE");
    }

    // ----- La liste (C15) -----

    @Test
    void shouldListEveryStatusWithItsVariantsSummed() throws Exception {
        mockMvc.perform(get(ADMIN + "/products").param("sort", "title").param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(19))
                .andExpect(jsonPath("$.content[0].title").value("Apple MacBook Air 13\" M3"))
                .andExpect(jsonPath("$.content[0].brand.name").value("Apple"))
                .andExpect(jsonPath("$.content[0].category.name").value("Ordinateurs portables"))
                .andExpect(jsonPath("$.content[0].variantCount").value(3))
                .andExpect(jsonPath("$.content[0].totalStock").value(12))
                .andExpect(jsonPath("$.content[0].lowStock").value(true));
    }

    @Test
    void shouldFilterOnTheStatus() throws Exception {
        mockMvc.perform(get(ADMIN + "/products").param("status", "BROUILLON"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].slug").value("corsair-vengeance-rgb-ddr5"));
        mockMvc.perform(get(ADMIN + "/products").param("status", "ARCHIVE"))
                .andExpect(jsonPath("$.content[*].slug", contains("logitech-mx-keys")));
    }

    @Test
    void shouldFindAProductByTheSkuOfOneOfItsVariants() throws Exception {
        mockMvc.perform(get(ADMIN + "/products").param("search", "g915tkl-az-bla"))
                .andExpect(jsonPath("$.content[*].slug", contains("logitech-g915-tkl")));
    }

    @Test
    void shouldIncludeTheSubCategories() throws Exception {
        mockMvc.perform(get(ADMIN + "/products").param("categoryId", String.valueOf(id("SELECT id FROM categorie WHERE slug = 'peripheriques'"))))
                .andExpect(jsonPath("$.totalElements").value(9))
                .andExpect(jsonPath("$.content[*].category.name", everyItem(org.hamcrest.Matchers.in(
                        java.util.List.of("Claviers", "Souris", "Casques")))));
    }

    @Test
    void shouldRefuseAnUnknownSortStatusOrPageSize() throws Exception {
        mockMvc.perform(get(ADMIN + "/products").param("sort", "price")).andExpect(status().isBadRequest());
        mockMvc.perform(get(ADMIN + "/products").param("status", "EN_LIGNE")).andExpect(status().isBadRequest());
        mockMvc.perform(get(ADMIN + "/products").param("size", "101")).andExpect(status().isBadRequest());
    }

    // ----- La fiche (C16) -----

    @Test
    void shouldShowWhatTheStorefrontHides() throws Exception {
        mockMvc.perform(get(ADMIN + "/products/" + productId("logitech-g915-tkl")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLIE"))
                .andExpect(jsonPath("$.vatRate.label").value("Taux normal"))
                .andExpect(jsonPath("$.specifications[0].key").value("Switches"))
                .andExpect(jsonPath("$.variants", hasSize(2)))
                .andExpect(jsonPath("$.variants[0].sku").value("LOGI-G915TKL-AZ-NOI"))
                .andExpect(jsonPath("$.variants[0].designation").value("Logitech G915 TKL - Noir, AZERTY"))
                .andExpect(jsonPath("$.variants[0].stock").value(8))
                .andExpect(jsonPath("$.variants[0].purchasePriceExclTax").isNumber())
                .andExpect(jsonPath("$.variants[0].values[0].attribute").value("Couleur"))
                .andExpect(jsonPath("$.variants[0].values[1].value").value("AZERTY"));
    }

    @Test
    void shouldAnswer404ForAnUnknownProduct() throws Exception {
        mockMvc.perform(get(ADMIN + "/products/999999")).andExpect(status().isNotFound());
    }

    @Test
    void shouldCreateADraftWithTheDefaultRateAndADerivedSlug() throws Exception {
        JsonNode created = json(mockMvc.perform(post(ADMIN + "/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(product("  Clavier Écho K2  ", null, "")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Clavier Écho K2"))
                .andExpect(jsonPath("$.slug").value("clavier-echo-k2"))
                .andExpect(jsonPath("$.status").value("BROUILLON"))
                .andExpect(jsonPath("$.vatRate.label").value("Taux normal"))
                .andExpect(jsonPath("$.universes[*].name", contains("Gaming")))
                .andExpect(jsonPath("$.specifications[*].key", contains("Garantie", "Connectique")))
                .andExpect(jsonPath("$.variants", hasSize(0))));

        mockMvc.perform(get(ADMIN + "/products/" + created.get("id").asLong()))
                .andExpect(jsonPath("$.specifications[1].value").value("USB-C"));
    }

    @Test
    void shouldRefuseATakenSlugAnUnknownBrandAndATwiceUsedKey() throws Exception {
        mockMvc.perform(post(ADMIN + "/products").contentType(MediaType.APPLICATION_JSON)
                        .content(product("Autre clavier", "logitech-g915-tkl", "")))
                .andExpect(status().isConflict());
        mockMvc.perform(post(ADMIN + "/products").contentType(MediaType.APPLICATION_JSON)
                        .content(product("Autre clavier", null, "").replaceFirst("\"brandId\": \\d+", "\"brandId\": 999999")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Brand 999999 does not exist"));
        mockMvc.perform(post(ADMIN + "/products").contentType(MediaType.APPLICATION_JSON)
                        .content(product("Autre clavier", null, ", {\"key\": \"garantie\", \"value\": \"3 ans\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Specification 'garantie' appears twice"));
    }

    @Test
    void shouldRefuseAnInactiveRateForANewProductButKeepItOnAnExistingOne() throws Exception {
        Long productId = productId("logitech-g915-tkl");
        jdbcTemplate.update("UPDATE tva SET actif = FALSE WHERE libelle = 'Taux normal'");
        String body = product("Clavier inactif", null, "")
                .replace("\"brandId\"", "\"vatRateId\": " + id("SELECT id FROM tva WHERE libelle = 'Taux normal'") + ", \"brandId\"");

        mockMvc.perform(post(ADMIN + "/products").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("VAT rate 'Taux normal' is inactive: choose an active rate"));
        mockMvc.perform(put(ADMIN + "/products/" + productId).contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("Clavier inactif", "Logitech G915 TKL")))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReplaceTheSpecificationsAndKeepTheSlug() throws Exception {
        Long id = productId("logitech-g915-tkl");
        // Garantie existe deja : elle est gardee, pas supprimee puis recreee (contrainte unique).
        mockMvc.perform(put(ADMIN + "/products/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(product("Logitech G915 TKL", null, "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("logitech-g915-tkl"))
                .andExpect(jsonPath("$.specifications[*].key", contains("Garantie", "Connectique")))
                .andExpect(jsonPath("$.specifications[0].value").value("2 ans"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM produit_specification WHERE produit_id = ?", Integer.class, id)).isEqualTo(2);
    }

    @Test
    void shouldSuggestTheKeysOfACategoryMostUsedFirst() throws Exception {
        mockMvc.perform(get(ADMIN + "/specification-keys")
                        .param("categoryId", String.valueOf(id("SELECT id FROM categorie WHERE slug = 'claviers'"))))
                .andExpect(jsonPath("$", contains("Connectique", "Garantie", "Switches", "Autonomie")));
    }

    // ----- Le statut -----

    @Test
    void shouldFollowTheWorkflow() throws Exception {
        Long id = createDraft();

        changeStatus(id, "PUBLIE").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("has no variant")));
        mockMvc.perform(post(ADMIN + "/products/" + id + "/variants").contentType(MediaType.APPLICATION_JSON)
                        .content(variant("K2-NOIR", "89.90", null, valueId("Couleur", "Noir"))))
                .andExpect(status().isCreated());
        changeStatus(id, "PUBLIE").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLIE"));
        changeStatus(id, "BROUILLON").andExpect(status().isConflict());
        changeStatus(id, "ARCHIVE").andExpect(status().isOk());
        changeStatus(id, "PUBLIE").andExpect(status().isOk());
    }

    @Test
    void shouldDeleteADraftOnly() throws Exception {
        mockMvc.perform(delete(ADMIN + "/products/" + productId("logitech-g915-tkl")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("archive it instead")));

        Long draft = productId("corsair-vengeance-rgb-ddr5");
        mockMvc.perform(delete(ADMIN + "/products/" + draft)).andExpect(status().isNoContent());
        mockMvc.perform(get(ADMIN + "/products/" + draft)).andExpect(status().isNotFound());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM variante WHERE sku = 'CORS-VENG-DDR5-32'", Integer.class)).isZero();
    }

    private ResultActions changeStatus(Long id, String status) throws Exception {
        return mockMvc.perform(put(ADMIN + "/products/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"" + status + "\"}"));
    }

    // ----- Les variantes (C17) -----

    @Test
    void shouldCreateAVariantWithItsInitialStock() throws Exception {
        Long id = createDraft();
        mockMvc.perform(post(ADMIN + "/products/" + id + "/variants").contentType(MediaType.APPLICATION_JSON)
                        .content(variant("K2-NOIR-AZ", "89.9", ", \"initialStock\": 12, \"alertThreshold\": 3",
                                valueId("Layout clavier", "AZERTY"), valueId("Couleur", "Noir"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.designation").value("Clavier Écho K2 - Noir, AZERTY"))
                .andExpect(jsonPath("$.salePriceExclTax").value(89.90))
                .andExpect(jsonPath("$.stock").value(12))
                .andExpect(jsonPath("$.alertThreshold").value(3))
                .andExpect(jsonPath("$.lowStock").value(false));
    }

    @Test
    void shouldRefuseTwoVariantsThatCannotBeToldApart() throws Exception {
        Long id = productId("logitech-g915-tkl");
        mockMvc.perform(post(ADMIN + "/products/" + id + "/variants").contentType(MediaType.APPLICATION_JSON)
                        .content(variant("G915-COPIE", "200", null,
                                valueId("Couleur", "Noir"), valueId("Layout clavier", "AZERTY"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Product 'logitech-g915-tkl' already has a variant with"
                        + " these values: LOGI-G915TKL-AZ-NOI (Logitech G915 TKL - Noir, AZERTY)"));
    }

    @Test
    void shouldRefuseTwoValuesOfOneAttribute() throws Exception {
        mockMvc.perform(post(ADMIN + "/products/" + createDraft() + "/variants").contentType(MediaType.APPLICATION_JSON)
                        .content(variant("K2-NB", "89", null, valueId("Couleur", "Noir"), valueId("Couleur", "Blanc"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("are both Couleur")));
    }

    @Test
    void shouldRefuseABadDiscountAnUnknownValueAndATakenSku() throws Exception {
        Long id = createDraft();
        mockMvc.perform(post(ADMIN + "/products/" + id + "/variants").contentType(MediaType.APPLICATION_JSON)
                        .content(variant("K2-A", "89", ", \"discountPriceExclTax\": 89")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("discountPriceExclTax must be below salePriceExclTax"));
        mockMvc.perform(post(ADMIN + "/products/" + id + "/variants").contentType(MediaType.APPLICATION_JSON)
                        .content(variant("K2-A", "89", null, 999999L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown attribute values: [999999]"));
        mockMvc.perform(post(ADMIN + "/products/" + id + "/variants").contentType(MediaType.APPLICATION_JSON)
                        .content(variant("LOGI-G915TKL-AZ-NOI", "89", null)))
                .andExpect(status().isConflict());
        mockMvc.perform(post(ADMIN + "/products/" + id + "/variants").contentType(MediaType.APPLICATION_JSON)
                        .content(variant("K2-A", "89.999", null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldUpdateEverythingButTheStock() throws Exception {
        Long variantId = id("SELECT id FROM variante WHERE sku = 'LOGI-G915TKL-AZ-NOI'");
        String body = variant("LOGI-G915TKL-AZ-NOI", "199.00", ", \"discountPriceExclTax\": 179.50",
                valueId("Couleur", "Noir"), valueId("Layout clavier", "AZERTY"));

        mockMvc.perform(put(ADMIN + "/variants/" + variantId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountPriceExclTax").value(179.50))
                .andExpect(jsonPath("$.stock").value(8));
        mockMvc.perform(put(ADMIN + "/variants/" + variantId).contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("}", ", \"initialStock\": 50}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("stock-adjustments")));
    }

    @Test
    void shouldDeleteAVariantOfADraftOnly() throws Exception {
        mockMvc.perform(delete(ADMIN + "/variants/" + id("SELECT id FROM variante WHERE sku = 'LOGI-G915TKL-AZ-NOI'")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("set the stock of 'LOGI-G915TKL-AZ-NOI' to 0")));

        Long draftVariant = id("SELECT id FROM variante WHERE sku = 'CORS-VENG-DDR5-32'");
        mockMvc.perform(delete(ADMIN + "/variants/" + draftVariant)).andExpect(status().isNoContent());
        mockMvc.perform(get(ADMIN + "/products/" + productId("corsair-vengeance-rgb-ddr5")))
                .andExpect(jsonPath("$.variants", hasSize(0)));
    }

    /**
     * A paid order takes a unit while the back-office saves the price of the
     * same variant. The save read the stock before the order: it must not
     * write that old value back and erase the sale (review of #154).
     *
     * <p>The outer transaction loads the variant first, as the save does,
     * then lets the order commit from another thread before the save
     * flushes: a deterministic version of the race.
     */
    @Test
    void shouldNotEraseASaleMadeWhileAVariantIsSaved() throws Exception {
        Long id = id("SELECT id FROM variante WHERE sku = 'LOGI-G915TKL-AZ-NOI'");
        AdminVariantRequest newPrice = new AdminVariantRequest("LOGI-G915TKL-AZ-NOI", new BigDecimal("120"),
                new BigDecimal("199.00"), null, null, new BigDecimal("1.025"),
                List.of(valueId("Couleur", "Noir"), valueId("Layout clavier", "AZERTY")), null, null);
        ExecutorService order = Executors.newSingleThreadExecutor();

        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            assertThat(variantRepository.findById(id).orElseThrow().getStock()).isEqualTo(8);
            try {
                order.submit(() -> stockService.takePaidOrder(Map.of(id, 1))).get(30, TimeUnit.SECONDS);
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
            variantService.update(id, newPrice);
        });
        order.shutdown();

        assertThat(jdbcTemplate.queryForObject("SELECT stock FROM variante WHERE id = ?", Integer.class, id))
                .isEqualTo(7);
        assertThat(jdbcTemplate.queryForObject("SELECT prix_vente_ht FROM variante WHERE id = ?", BigDecimal.class, id))
                .isEqualByComparingTo("199.00");
    }

    /**
     * Someone publishes a product while a colleague saves its sheet, which
     * still shows it as a draft: the save must not put the draft status back.
     * Otherwise a product that has been on sale could be deleted.
     */
    @Test
    void shouldNotPutBackTheDraftStatusWhenASheetIsSaved() throws Exception {
        Long id = productId("corsair-vengeance-rgb-ddr5");
        AdminProductRequest sheet = new AdminProductRequest("Corsair Vengeance RGB DDR5", null, "<p>DDR5</p>",
                id("SELECT id FROM marque WHERE slug = 'corsair'"), id("SELECT id FROM categorie WHERE slug = 'memoire'"),
                null, List.of(), List.of(new AdminProductRequest.Specification("Fréquence", "6000 MHz")));
        ExecutorService colleague = Executors.newSingleThreadExecutor();

        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            assertThat(productRepository.findById(id).orElseThrow().getStatus()).isEqualTo(ProductStatus.BROUILLON);
            try {
                colleague.submit(() -> productService.changeStatus(id, ProductStatus.PUBLIE)).get(30, TimeUnit.SECONDS);
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
            productService.update(id, sheet);
        });
        colleague.shutdown();

        assertThat(jdbcTemplate.queryForObject("SELECT statut FROM produit WHERE id = ?", String.class, id))
                .isEqualTo("PUBLIE");
    }

    /** A draft keyboard with no variant yet. */
    private Long createDraft() throws Exception {
        return json(mockMvc.perform(post(ADMIN + "/products").contentType(MediaType.APPLICATION_JSON)
                        .content(product("Clavier Écho K2", null, "")))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private String product(String title, String slug, String moreSpecifications) {
        return """
                {"title": "%s", %s"description": "<p>Mécanique</p>",
                 "brandId": %s, "categoryId": %s, "universeIds": [%s],
                 "specifications": [{"key": "Garantie", "value": "2 ans"}, {"key": "Connectique", "value": "USB-C"}%s]}
                """.formatted(title, slug == null ? "" : "\"slug\": \"" + slug + "\", ",
                id("SELECT id FROM marque WHERE slug = 'logitech'"),
                id("SELECT id FROM categorie WHERE slug = 'claviers'"),
                id("SELECT id FROM univers WHERE slug = 'gaming'"),
                moreSpecifications);
    }

    private static String variant(String sku, String salePrice, String more, Long... valueIds) {
        return """
                {"sku": "%s", "purchasePriceExclTax": 50, "salePriceExclTax": %s, "ecoTax": 0.42,
                 "weightKg": 0.9, "valueIds": [%s]%s}
                """.formatted(sku, salePrice,
                String.join(", ", java.util.Arrays.stream(valueIds).map(String::valueOf).toList()),
                more == null ? "" : more);
    }

    private Long valueId(String attribute, String value) {
        return id("SELECT v.id FROM valeur_attribut v JOIN attribut a ON a.id = v.attribut_id"
                + " WHERE a.nom = '" + attribute + "' AND v.valeur = '" + value + "'");
    }

    private Long productId(String slug) {
        return id("SELECT id FROM produit WHERE slug = '" + slug + "'");
    }

    private Long id(String sql) {
        return jdbcTemplate.queryForObject(sql, Long.class);
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsByteArray());
    }
}
