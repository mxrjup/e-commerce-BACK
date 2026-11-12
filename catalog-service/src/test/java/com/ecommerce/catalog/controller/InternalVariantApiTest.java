package com.ecommerce.catalog.controller;

import com.ecommerce.catalog.demo.DemoCatalogLoader;
import com.ecommerce.catalog.support.CatalogTestData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The internal variant API (C10) over the demonstration catalogue, in a
 * database of its own.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:cataloginternal;MODE=PostgreSQL;"
        + "DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class InternalVariantApiTest {

    private static final String VARIANTS = "/api/internal/catalog/variants";

    /** The four fields order-service reads, as its CatalogVariantResponse declares them. */
    record OrderServiceView(Long id, String designation, BigDecimal unitPriceExclTax, BigDecimal vatRate) {
    }

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

    @Test
    void shouldServeAVariantWithItsFrozenFieldsAndItsDetails() throws Exception {
        long id = idOf("LOGI-G915TKL-AZ-NOI");

        mockMvc.perform(get(VARIANTS + "/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.productSlug").value("logitech-g915-tkl"))
                .andExpect(jsonPath("$.designation").value("Logitech G915 TKL - Noir, AZERTY"))
                // Prix remise 166,66 + ecotaxe 0,10.
                .andExpect(jsonPath("$.unitPriceExclTax").value(166.76))
                .andExpect(jsonPath("$.salePriceExclTax").value(191.66))
                .andExpect(jsonPath("$.discountPriceExclTax").value(166.66))
                .andExpect(jsonPath("$.ecoTax").value(0.10))
                .andExpect(jsonPath("$.vatRate").value(20.00))
                .andExpect(jsonPath("$.purchasePriceExclTax").value(130.00))
                .andExpect(jsonPath("$.weightKg").value(0.810))
                .andExpect(jsonPath("$.stock").value(8))
                .andExpect(jsonPath("$.status").value("PUBLIE"))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.imageUrl").value(nullValue()));
    }

    @Test
    void shouldStayReadableByOrderService() throws Exception {
        long id = idOf("SAMS-ODG7-27");
        String body = mockMvc.perform(get(VARIANTS + "/{id}", id))
                .andReturn().getResponse().getContentAsString();

        // Le RestClient d'order-service lit avec un ObjectMapper construit ainsi.
        ObjectMapper orderServiceMapper = Jackson2ObjectMapperBuilder.json().build();
        OrderServiceView view = orderServiceMapper.readValue(body, OrderServiceView.class);

        assertThat(view.id()).isEqualTo(id);
        // Sans valeur d'attribut, la designation est le titre seul.
        assertThat(view.designation()).isEqualTo("Samsung Odyssey G7 27\"");
        assertThat(view.unitPriceExclTax()).isEqualByComparingTo("460.83");
        assertThat(view.vatRate()).isEqualByComparingTo("20.00");
    }

    @Test
    void shouldTellWhatCannotBeBought() throws Exception {
        mockMvc.perform(get(VARIANTS + "/{id}", idOf("CORS-K70PRO-AZ-NOI")))
                .andExpect(jsonPath("$.stock").value(0))
                .andExpect(jsonPath("$.available").value(false));

        mockMvc.perform(get(VARIANTS + "/{id}", idOf("CORS-VENG-DDR5-32")))
                .andExpect(jsonPath("$.status").value("BROUILLON"))
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void shouldGiveTheMainPhotoOnce() throws Exception {
        long id = idOf("LOGI-MXKEYS-S-AZ-BLA");
        jdbcTemplate.update("INSERT INTO variante_image (variante_id, url, position) VALUES (?, ?, 2), (?, ?, 1)",
                id, "/images/mx-keys-s-blanc-2.jpg", id, "/images/mx-keys-s-blanc-1.jpg");

        mockMvc.perform(get(VARIANTS).param("ids", String.valueOf(id)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].imageUrl").value("/images/mx-keys-s-blanc-1.jpg"));
    }

    @Test
    void shouldAnswerNotFoundForAnUnknownVariant() throws Exception {
        mockMvc.perform(get(VARIANTS + "/{id}", 999_999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void shouldServeSeveralVariantsInTheOrderAskedAndSkipTheUnknownOnes() throws Exception {
        long mouse = idOf("LOGI-MXM3S-GRI");
        long ssd = idOf("SAMS-990PRO-2T");

        mockMvc.perform(get(VARIANTS).param("ids", ssd + ",999999," + mouse + "," + ssd))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].sku").value(contains("SAMS-990PRO-2T", "LOGI-MXM3S-GRI")));
    }

    @Test
    void shouldRefuseAnEmptyOrTooLongListOfIds() throws Exception {
        mockMvc.perform(get(VARIANTS)).andExpect(status().isBadRequest());
        mockMvc.perform(get(VARIANTS).param("ids", "")).andExpect(status().isBadRequest());

        String tooMany = IntStream.rangeClosed(1, 101).mapToObj(String::valueOf).collect(Collectors.joining(","));
        mockMvc.perform(get(VARIANTS).param("ids", tooMany))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("ids must contain at most 100 variant ids"));

        mockMvc.perform(get(VARIANTS).param("ids", "abc")).andExpect(status().isBadRequest());
    }

    private long idOf(String sku) {
        return jdbcTemplate.queryForObject("SELECT id FROM variante WHERE sku = ?", Long.class, sku);
    }
}
