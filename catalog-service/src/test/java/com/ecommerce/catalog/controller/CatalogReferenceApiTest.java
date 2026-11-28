package com.ecommerce.catalog.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The reference data API end to end: HTTP, validation, services, and the
 * schema built by the real Flyway migrations.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CatalogReferenceApiTest {

    private static final String ADMIN = "/api/admin/catalog";
    private static final String PUBLIC = "/api/catalog";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Back to the state right after the migrations: no entries, the four seeded VAT rates. */
    @BeforeEach
    void resetData() {
        jdbcTemplate.update("UPDATE categorie SET parent_id = NULL");
        jdbcTemplate.update("DELETE FROM categorie");
        jdbcTemplate.update("DELETE FROM marque");
        jdbcTemplate.update("DELETE FROM univers");
        jdbcTemplate.update("DELETE FROM tva WHERE libelle NOT IN "
                + "('Taux normal', 'Taux intermédiaire', 'Taux réduit', 'Taux particulier')");
        jdbcTemplate.update("UPDATE tva SET par_defaut = (libelle = 'Taux normal')");
    }

    @Nested
    class Brands {

        @Test
        void shouldCreateABrandWithASlugDerivedFromItsName() throws Exception {
            create("brands", "{\"name\":\"  Logitech G \",\"logoUrl\":\"https://cdn.test/logi.png\"}")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.name").value("Logitech G"))
                    .andExpect(jsonPath("$.slug").value("logitech-g"));

            mockMvc.perform(get(PUBLIC + "/brands/logitech-g"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.logoUrl").value("https://cdn.test/logi.png"));
        }

        @Test
        void shouldListBrandsByName() throws Exception {
            create("brands", "{\"name\":\"Razer\"}");
            create("brands", "{\"name\":\"Asus\"}");

            mockMvc.perform(get(PUBLIC + "/brands"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].name", contains("Asus", "Razer")));
        }

        @Test
        void shouldRefuseANameAlreadyTakenWhateverItsCase() throws Exception {
            create("brands", "{\"name\":\"Razer\"}");

            create("brands", "{\"name\":\"RAZER\",\"slug\":\"razer-2\"}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.message").value("A brand named 'RAZER' already exists"));
        }

        @Test
        void shouldRefuseASlugAlreadyTaken() throws Exception {
            create("brands", "{\"name\":\"Razer\"}");

            create("brands", "{\"name\":\"Razer Inc\",\"slug\":\"razer\"}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("A brand with slug 'razer' already exists"));
        }

        @Test
        void shouldRejectAnInvalidRequest() throws Exception {
            create("brands", "{\"name\":\"\",\"slug\":\"Not A Slug\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details", hasItem("name must not be blank")))
                    .andExpect(jsonPath("$.details", hasItem("slug must be lowercase words separated by hyphens")));
        }

        @Test
        void shouldUpdateABrandKeepingItsOwnNameAndSlug() throws Exception {
            long id = idOf(create("brands", "{\"name\":\"Razer\"}"));

            mockMvc.perform(put(ADMIN + "/brands/" + id).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Razer\",\"slug\":\"razer\",\"description\":\"Gaming\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.description").value("Gaming"));
        }

        @Test
        void shouldKeepTheSlugWhenABrandIsRenamedWithoutOne() throws Exception {
            long id = idOf(create("brands", "{\"name\":\"Asus\"}"));

            mockMvc.perform(put(ADMIN + "/brands/" + id).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"ASUS ROG\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("ASUS ROG"))
                    .andExpect(jsonPath("$.slug").value("asus"));
            mockMvc.perform(get(PUBLIC + "/brands/asus")).andExpect(status().isOk());

            mockMvc.perform(put(ADMIN + "/brands/" + id).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"ASUS ROG\",\"slug\":\"asus-rog\"}"))
                    .andExpect(jsonPath("$.slug").value("asus-rog"));
        }

        @Test
        void shouldAskForASlugWhenNoneCanBeDerivedFromTheName() throws Exception {
            create("brands", "{\"name\":\"日本語\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message",
                            containsString("No slug can be derived from the name")));
            create("brands", "{\"name\":\"+++\"}").andExpect(status().isBadRequest());
            create("brands", "{\"name\":\"" + "œ".repeat(100) + "\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", containsString("longer than 120 characters")));

            create("brands", "{\"name\":\"日本語\",\"slug\":\"nihongo\"}")
                    .andExpect(status().isCreated());
        }

        @Test
        void shouldDeleteABrand() throws Exception {
            long id = idOf(create("brands", "{\"name\":\"Razer\"}"));

            mockMvc.perform(delete(ADMIN + "/brands/" + id)).andExpect(status().isNoContent());

            mockMvc.perform(get(ADMIN + "/brands/" + id))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Brand " + id + " not found"));
        }

        @Test
        void shouldAnswerNotFoundForAnUnknownSlug() throws Exception {
            mockMvc.perform(get(PUBLIC + "/brands/unknown"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Brand 'unknown' not found"));
        }
    }

    @Nested
    class Universes {

        @Test
        void shouldListUniversesByPositionThenName() throws Exception {
            create("universes", "{\"name\":\"Télétravail\",\"position\":2}")
                    .andExpect(jsonPath("$.slug").value("teletravail"));
            create("universes", "{\"name\":\"Streaming\",\"position\":1}");
            create("universes", "{\"name\":\"Gaming\",\"position\":1}");

            mockMvc.perform(get(PUBLIC + "/universes"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].name", contains("Gaming", "Streaming", "Télétravail")));
        }

        @Test
        void shouldKeepTheSlugWhenAUniverseIsRenamedWithoutOne() throws Exception {
            long id = idOf(create("universes", "{\"name\":\"Gaming\"}"));

            mockMvc.perform(put(ADMIN + "/universes/" + id).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Gaming & e-sport\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.slug").value("gaming"));
        }

        @Test
        void shouldRejectANegativePosition() throws Exception {
            create("universes", "{\"name\":\"Gaming\",\"position\":-1}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details", hasItem("position must be positive or zero")));
        }
    }

    @Nested
    class Categories {

        @Test
        void shouldServeTheTreeToTheStorefront() throws Exception {
            long peripherals = idOf(create("categories", "{\"name\":\"Périphériques\"}"));
            long mice = idOf(create("categories", "{\"name\":\"Souris\",\"parentId\":" + peripherals + "}"));
            create("categories", "{\"name\":\"Souris sans fil\",\"parentId\":" + mice + "}");
            create("categories", "{\"name\":\"Claviers\",\"parentId\":" + peripherals + "}");
            create("categories", "{\"name\":\"Audio\"}");

            mockMvc.perform(get(PUBLIC + "/categories"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].slug", contains("audio", "peripheriques")))
                    .andExpect(jsonPath("$[1].children[*].name", contains("Claviers", "Souris")))
                    .andExpect(jsonPath("$[1].children[1].children[0].slug").value("souris-sans-fil"))
                    .andExpect(jsonPath("$[0].children", hasSize(0)));

            mockMvc.perform(get(ADMIN + "/categories"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(5)));

            mockMvc.perform(get(PUBLIC + "/categories/souris"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.parentId").value(peripherals));
        }

        @Test
        void shouldAllowTheSameNameInTwoBranchesWithDistinctSlugs() throws Exception {
            long mice = idOf(create("categories", "{\"name\":\"Souris\"}"));
            long keyboards = idOf(create("categories", "{\"name\":\"Claviers\"}"));
            create("categories", "{\"name\":\"Accessoires\",\"parentId\":" + mice + "}")
                    .andExpect(status().isCreated());

            create("categories", "{\"name\":\"Accessoires\",\"parentId\":" + keyboards + "}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("A category with slug 'accessoires' already exists"));
            create("categories", "{\"name\":\"Accessoires\",\"slug\":\"accessoires-claviers\",\"parentId\":"
                    + keyboards + "}")
                    .andExpect(status().isCreated());
        }

        @Test
        void shouldRefuseToMoveACategoryIntoItsOwnBranch() throws Exception {
            long root = idOf(create("categories", "{\"name\":\"Périphériques\"}"));
            long child = idOf(create("categories", "{\"name\":\"Souris\",\"parentId\":" + root + "}"));

            mockMvc.perform(put(ADMIN + "/categories/" + root).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Périphériques\",\"parentId\":" + child + "}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message", containsString("cannot be moved under itself")));

            mockMvc.perform(put(ADMIN + "/categories/" + root).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Périphériques\",\"parentId\":" + root + "}"))
                    .andExpect(status().isConflict());
        }

        @Test
        void shouldMoveACategoryToAnotherBranch() throws Exception {
            long first = idOf(create("categories", "{\"name\":\"Gaming\"}"));
            long second = idOf(create("categories", "{\"name\":\"Bureau\"}"));
            long child = idOf(create("categories", "{\"name\":\"Casques\",\"parentId\":" + first + "}"));

            mockMvc.perform(put(ADMIN + "/categories/" + child).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Casques\",\"parentId\":" + second + "}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.parentId").value(second));
        }

        @Test
        void shouldKeepTheSlugWhenACategoryIsUpdatedWithoutOne() throws Exception {
            long id = idOf(create("categories", "{\"name\":\"Accessoires\",\"slug\":\"accessoires-audio\"}"));

            mockMvc.perform(put(ADMIN + "/categories/" + id).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Accessoires\",\"position\":3}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.slug").value("accessoires-audio"))
                    .andExpect(jsonPath("$.position").value(3));
        }

        @Test
        void shouldRefuseAnUnknownParent() throws Exception {
            create("categories", "{\"name\":\"Souris\",\"parentId\":999999}")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Parent category 999999 not found"));
        }

        @Test
        void shouldOnlyDeleteACategoryWithoutSubCategories() throws Exception {
            long root = idOf(create("categories", "{\"name\":\"Périphériques\"}"));
            long child = idOf(create("categories", "{\"name\":\"Souris\",\"parentId\":" + root + "}"));

            mockMvc.perform(delete(ADMIN + "/categories/" + root))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message", containsString("has sub-categories")));

            mockMvc.perform(delete(ADMIN + "/categories/" + child)).andExpect(status().isNoContent());
            mockMvc.perform(delete(ADMIN + "/categories/" + root)).andExpect(status().isNoContent());
        }
    }

    @Nested
    class VatRates {

        @Test
        void shouldStartWithTheFrenchRatesAndTheStandardOneAsDefault() throws Exception {
            mockMvc.perform(get(ADMIN + "/vat-rates"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].rate", contains(20.00, 10.00, 5.50, 2.10)))
                    .andExpect(jsonPath("$[0].label").value("Taux normal"))
                    .andExpect(jsonPath("$[0].defaultRate").value(true))
                    .andExpect(jsonPath("$[?(@.defaultRate == true)]", hasSize(1)));
        }

        @Test
        void shouldMoveTheDefaultFlagToANewDefaultRate() throws Exception {
            create("vat-rates", "{\"label\":\"Taux Corse\",\"rate\":13.00,\"defaultRate\":true}")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.defaultRate").value(true));

            mockMvc.perform(get(ADMIN + "/vat-rates"))
                    .andExpect(jsonPath("$[?(@.defaultRate == true)].label", contains("Taux Corse")));
        }

        @Test
        void shouldLeaveExactlyOneDefaultWhenSeveralRatesClaimItAtOnce() throws Exception {
            int writers = 6;
            ExecutorService pool = Executors.newFixedThreadPool(writers);
            CountDownLatch start = new CountDownLatch(1);
            try {
                List<Future<Integer>> results = new ArrayList<>();
                for (int i = 0; i < writers; i++) {
                    String json = "{\"label\":\"Concurrent " + i + "\",\"rate\":" + (11 + i) + ",\"defaultRate\":true}";
                    results.add(pool.submit(() -> {
                        start.await();
                        return create("vat-rates", json).andReturn().getResponse().getStatus();
                    }));
                }
                start.countDown();
                for (Future<Integer> result : results) {
                    assertThat(result.get(30, TimeUnit.SECONDS)).isEqualTo(201);
                }
            } finally {
                pool.shutdownNow();
            }

            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tva WHERE par_defaut", Integer.class))
                    .isEqualTo(1);
        }

        @Test
        void shouldKeepTheDefaultRateUntilAnotherOneTakesOver() throws Exception {
            long standard = idOfVatRate("Taux normal");

            mockMvc.perform(put(ADMIN + "/vat-rates/" + standard).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"label\":\"Taux normal\",\"rate\":20.00,\"defaultRate\":false}"))
                    .andExpect(status().isConflict());
            mockMvc.perform(delete(ADMIN + "/vat-rates/" + standard))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message", containsString("is the default VAT rate")));
        }

        @Test
        void shouldDeleteARateThatIsNotTheDefault() throws Exception {
            long id = idOf(create("vat-rates", "{\"label\":\"Taux Corse\",\"rate\":13.00}"));

            mockMvc.perform(delete(ADMIN + "/vat-rates/" + id)).andExpect(status().isNoContent());
        }

        @Test
        void shouldRejectARateOutOfRangeOrTooPrecise() throws Exception {
            create("vat-rates", "{\"label\":\"Faux\",\"rate\":120}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details", hasItem("rate must be between 0 and 100")));
            create("vat-rates", "{\"label\":\"Faux\",\"rate\":5.555}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details", hasItem("rate must have at most 2 decimals")));
        }

        @Test
        void shouldNotExposeVatRatesOnThePublicApi() throws Exception {
            mockMvc.perform(get(PUBLIC + "/vat-rates")).andExpect(status().isNotFound());
        }

        private long idOfVatRate(String label) {
            return jdbcTemplate.queryForObject("SELECT id FROM tva WHERE libelle = ?", Long.class, label);
        }
    }

    private ResultActions create(String resource, String json) throws Exception {
        return mockMvc.perform(post(ADMIN + "/" + resource).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long idOf(ResultActions created) throws Exception {
        JsonNode body = objectMapper.readTree(created.andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        return body.get("id").asLong();
    }
}
