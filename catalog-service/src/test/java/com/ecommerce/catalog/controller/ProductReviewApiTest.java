package com.ecommerce.catalog.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:catalogreviews;MODE=PostgreSQL;"
        + "DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class ProductReviewApiTest {

    private static final String PRODUCTS = "/api/catalog/products";
    private static final String REVIEWS = PRODUCTS + "/logitech-g915-tkl/reviews";
    private static final String VALID_BODY = """
            {"rating":4,"comment":"Good product"}
            """;

    @Autowired
    private MockMvc mvc;

    @Test
    void publishesUpdatesAndPaginatesReviewsWithServerStatistics() throws Exception {
        mvc.perform(get(REVIEWS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviews").isEmpty())
                .andExpect(jsonPath("$.averageRating").value(nullValue()))
                .andExpect(jsonPath("$.totalReviews").value(0));

        var original = mvc.perform(review(REVIEWS, """
                        {"rating":1,"comment":"First review"}
                        """).header("X-Auth-Subject", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.createdAt").isString())
                .andReturn().getResponse().getContentAsString();
        mvc.perform(get(REVIEWS))
                .andExpect(jsonPath("$.averageRating").value(1.0))
                .andExpect(jsonPath("$.totalReviews").value(1));

        mvc.perform(review(REVIEWS, """
                        {"rating":5,"comment":"Second review","authorSubject":"alice"}
                        """).header("X-Auth-Subject", "bob").header("X-User-Id", "alice"))
                .andExpect(status().isOk());
        mvc.perform(get(REVIEWS).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").value(3.0))
                .andExpect(jsonPath("$.totalReviews").value(2))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.reviews[0].comment").value("Second review"))
                .andExpect(jsonPath("$.reviews[0].authorSubject").doesNotExist());

        var updated = mvc.perform(review(REVIEWS, VALID_BODY).header("X-Auth-Subject", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(4))
                .andReturn().getResponse().getContentAsString();
        var mapper = new ObjectMapper();
        assertEquals(mapper.readTree(original).get("id"), mapper.readTree(updated).get("id"));
        Instant created = Instant.parse(mapper.readTree(original).get("createdAt").asText());
        Instant revised = Instant.parse(mapper.readTree(updated).get("createdAt").asText());
        assertTrue(Math.abs(ChronoUnit.MICROS.between(created, revised)) <= 1);

        mvc.perform(get(REVIEWS).param("size", "1").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviews[0].comment").value("Good product"))
                .andExpect(jsonPath("$.averageRating").value(4.5))
                .andExpect(jsonPath("$.totalReviews").value(2))
                .andExpect(jsonPath("$.hasNext").value(false));
        mvc.perform(get(REVIEWS).param("size", "1").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviews").isEmpty())
                .andExpect(jsonPath("$.totalReviews").value(2));
        mvc.perform(get(REVIEWS).param("size", "51")).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "6", "4.5", "4.0", "\"4\"", "null", "4294967300"})
    void rejectsInvalidRatings(String rating) throws Exception {
        mvc.perform(review(REVIEWS, "{\"rating\":" + rating + ",\"comment\":\"ok\"}")
                        .header("X-Auth-Subject", "invalid-rating"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInvalidComments() throws Exception {
        for (String comment : new String[]{"\"\"", "\"   \"", "42", "\"" + "a".repeat(2001) + "\""}) {
            mvc.perform(review(REVIEWS, "{\"rating\":4,\"comment\":" + comment + "}")
                            .header("X-Auth-Subject", "invalid-comment"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void rejectsWritesWithoutGatewayIdentity() throws Exception {
        mvc.perform(review(REVIEWS, VALID_BODY).header("X-User-Id", "alice"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown-product", "logitech-mx-keys"})
    void rejectsProductsOutsideTheStorefront(String slug) throws Exception {
        String path = PRODUCTS + "/" + slug + "/reviews";
        mvc.perform(get(path)).andExpect(status().isNotFound());
        mvc.perform(review(path, VALID_BODY).header("X-Auth-Subject", "alice"))
                .andExpect(status().isNotFound());
    }

    private static MockHttpServletRequestBuilder review(String path, String body) {
        return post(path).contentType(APPLICATION_JSON).content(body);
    }
}
