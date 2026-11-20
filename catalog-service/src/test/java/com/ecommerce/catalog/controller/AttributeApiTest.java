package com.ecommerce.catalog.controller;

import com.ecommerce.catalog.demo.DemoCatalogLoader;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The attributes and their values (C04), over the demonstration catalogue. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:catalogattributes;MODE=PostgreSQL;"
        + "DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class AttributeApiTest {

    private static final String ATTRIBUTES = "/api/admin/catalog/attributes";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    private DemoCatalogLoader loader;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void loadIfEmpty() {
        loader.run(null);
    }

    @AfterEach
    void tearDown() {
        CatalogTestData.clear(jdbcTemplate);
    }

    @Test
    void shouldListTheAttributesWithTheirValuesInOrder() throws Exception {
        mockMvc.perform(get(ATTRIBUTES))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", contains("Capacité", "Configuration", "Couleur", "Layout clavier")))
                .andExpect(jsonPath("$[2].values[*].value", contains("Noir", "Blanc", "Gris sidéral", "Argent")));
    }

    @Test
    void shouldCreateAnAttributeAndRefuseItsTwin() throws Exception {
        mockMvc.perform(post(ATTRIBUTES).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \" Connectivité \", \"values\": [{\"value\": \"Filaire\"}, {\"value\": \"Sans fil\"}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Connectivité"))
                .andExpect(jsonPath("$.values[*].value", contains("Filaire", "Sans fil")))
                .andExpect(jsonPath("$.values[1].position").value(1));

        mockMvc.perform(post(ATTRIBUTES).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"connectivité\", \"values\": []}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post(ATTRIBUTES).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Taille\", \"values\": [{\"value\": \"M\"}, {\"value\": \" m \"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Value 'm' appears twice"));
    }

    /**
     * One save that renames, reorders, adds, removes an unused value, and
     * sends an existing value back without its id: all in one go, without
     * hitting the unique constraint on (attribute, value).
     */
    @Test
    void shouldReplaceTheValuesInOneSave() throws Exception {
        JsonNode capacity = attribute("Capacité");
        long oneTb = valueId(capacity, "1 To");
        long twoTb = valueId(capacity, "2 To");
        long ram32 = valueId(capacity, "32 Go (2 x 16 Go)");
        long ram64 = valueId(capacity, "64 Go (2 x 32 Go)");
        Long unused = createValue(capacity.get("id").asLong(), "4 To");

        mockMvc.perform(put(ATTRIBUTES + "/" + capacity.get("id").asLong()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Capacité", "values": [
                                  {"id": %d, "value": "2 To"}, {"id": %d, "value": "1 To (NVMe)"},
                                  {"value": "8 To"}, {"value": "32 Go (2 x 16 Go)"}, {"id": %d, "value": "64 Go (2 x 32 Go)"}]}
                                """.formatted(twoTb, oneTb, ram64)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.values[*].value",
                        contains("2 To", "1 To (NVMe)", "8 To", "32 Go (2 x 16 Go)", "64 Go (2 x 32 Go)")))
                .andExpect(jsonPath("$.values[1].id").value(oneTb))
                .andExpect(jsonPath("$.values[3].id").value(ram32))
                .andExpect(jsonPath("$.values[4].position").value(4));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM valeur_attribut WHERE id = ?", Integer.class, unused))
                .isZero();
        mockMvc.perform(get("/api/admin/catalog/inventory").param("search", "SAMS-990PRO-1T"))
                .andExpect(jsonPath("$.content[0].designation").value("Samsung 990 Pro - 1 To (NVMe)"));
    }

    @Test
    void shouldKeepAValueAVariantUses() throws Exception {
        JsonNode color = attribute("Couleur");
        mockMvc.perform(put(ATTRIBUTES + "/" + color.get("id").asLong()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Couleur\", \"values\": [{\"id\": %d, \"value\": \"Noir\"}]}"
                                .formatted(valueId(color, "Noir"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Value 'Blanc' of Couleur is used by variants and cannot be removed"));
    }

    @Test
    void shouldRefuseAValueOfAnotherAttribute() throws Exception {
        JsonNode color = attribute("Couleur");
        long azerty = valueId(attribute("Layout clavier"), "AZERTY");
        mockMvc.perform(put(ATTRIBUTES + "/" + color.get("id").asLong()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Couleur\", \"values\": [{\"id\": %d, \"value\": \"AZERTY\"}]}".formatted(azerty)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("does not belong to attribute 'Couleur'")));
    }

    @Test
    void shouldDeleteOnlyAnUnusedAttribute() throws Exception {
        mockMvc.perform(delete(ATTRIBUTES + "/" + attribute("Couleur").get("id").asLong()))
                .andExpect(status().isConflict());

        JsonNode created = objectMapper.readTree(mockMvc.perform(post(ATTRIBUTES).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Taille\", \"values\": [{\"value\": \"M\"}]}"))
                .andReturn().getResponse().getContentAsByteArray());
        mockMvc.perform(delete(ATTRIBUTES + "/" + created.get("id").asLong())).andExpect(status().isNoContent());
        mockMvc.perform(get(ATTRIBUTES + "/" + created.get("id").asLong())).andExpect(status().isNotFound());
    }

    private Long createValue(long attributeId, String value) {
        jdbcTemplate.update("INSERT INTO valeur_attribut (attribut_id, valeur, position) VALUES (?, ?, 99)",
                attributeId, value);
        return jdbcTemplate.queryForObject("SELECT id FROM valeur_attribut WHERE valeur = ?", Long.class, value);
    }

    private JsonNode attribute(String name) throws Exception {
        JsonNode all = objectMapper.readTree(mockMvc.perform(get(ATTRIBUTES)).andReturn().getResponse()
                .getContentAsByteArray());
        for (JsonNode attribute : all) {
            if (attribute.get("name").asText().equals(name)) {
                return attribute;
            }
        }
        throw new AssertionError("No attribute " + name);
    }

    private static long valueId(JsonNode attribute, String value) {
        for (JsonNode node : attribute.get("values")) {
            if (node.get("value").asText().equals(value)) {
                return node.get("id").asLong();
            }
        }
        throw new AssertionError("No value " + value);
    }
}
