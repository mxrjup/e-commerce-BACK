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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The photos of the variants (C06), stored in a real temporary directory,
 * over the demonstration catalogue.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:catalogimages;MODE=PostgreSQL;"
        + "DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class VariantImageApiTest {

    static final Path DIRECTORY = temporaryDirectory();
    static final String PUBLIC_URL = "http://localhost:8080/api/catalog/images/";

    /** The first bytes of each format, then some payload. */
    static final byte[] JPEG = bytes(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}, "JFIF photo");
    static final byte[] PNG = bytes(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'}, "png photo");
    static final byte[] WEBP = bytes("RIFF\0\0\0\0WEBP".getBytes(StandardCharsets.US_ASCII), "webp photo");

    @DynamicPropertySource
    static void images(DynamicPropertyRegistry registry) {
        registry.add("catalog.images.directory", DIRECTORY::toString);
    }

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
    void tearDown() throws IOException {
        CatalogTestData.clear(jdbcTemplate);
        try (Stream<Path> files = Files.list(DIRECTORY)) {
            for (Path file : files.toList()) {
                Files.deleteIfExists(file);
            }
        }
    }

    @Test
    void shouldStoreAPhotoAndServeItPublicly() throws Exception {
        JsonNode variant = json(upload(variantId("LOGI-G915TKL-AZ-NOI"), "photo.jpg", JPEG)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.images", hasSize(1)))
                .andExpect(jsonPath("$.images[0].position").value(1))
                .andExpect(jsonPath("$.images[0].url", startsWith(PUBLIC_URL))));
        String url = variant.at("/images/0/url").asText();
        String name = url.substring(PUBLIC_URL.length());
        assertThat(name).matches("[0-9a-f]{32}\\.jpg");
        assertThat(DIRECTORY.resolve(name)).hasBinaryContent(JPEG);

        mockMvc.perform(get("/api/catalog/images/" + name))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(content().bytes(JPEG))
                .andExpect(header().string("Cache-Control", containsString("max-age=31536000")))
                .andExpect(header().string("Cache-Control", containsString("immutable")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        // La vitrine voit la photo sur la fiche du produit.
        mockMvc.perform(get("/api/catalog/products/logitech-g915-tkl"))
                .andExpect(jsonPath("$.variants[0].images[0]").value(url));
    }

    @Test
    void shouldRecogniseTheFormatFromTheContentNotTheName() throws Exception {
        Long id = variantId("LOGI-G915TKL-AZ-NOI");
        upload(id, "photo.jpg", PNG).andExpect(jsonPath("$.images[0].url", containsString(".png")));
        upload(id, "photo", WEBP).andExpect(jsonPath("$.images[1].url", containsString(".webp")))
                .andExpect(jsonPath("$.images[1].position").value(2));

        upload(id, "virus.jpg", "MZ this is not an image".getBytes(StandardCharsets.US_ASCII))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only JPEG, PNG and WebP images are accepted"));
        assertThat(files()).hasSize(2);
    }

    @Test
    void shouldRefuseATooLargeFileAndAnEleventhPhoto() throws Exception {
        Long id = variantId("LOGI-G915TKL-AZ-NOI");
        byte[] large = Arrays.copyOf(JPEG, 5 * 1024 * 1024 + 1);
        upload(id, "large.jpg", large).andExpect(status().isPayloadTooLarge());

        for (int i = 0; i < 10; i++) {
            upload(id, "photo" + i + ".jpg", JPEG).andExpect(status().isCreated());
        }
        upload(id, "eleventh.jpg", JPEG)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("already has 10 images")));
        assertThat(files()).hasSize(10);
    }

    @Test
    void shouldDeleteAPhotoAndItsFileAndCloseTheGap() throws Exception {
        Long id = variantId("LOGI-G915TKL-AZ-NOI");
        upload(id, "a.jpg", JPEG);
        upload(id, "b.png", PNG);
        JsonNode variant = json(upload(id, "c.webp", WEBP));
        long second = variant.at("/images/1/id").asLong();
        String secondName = variant.at("/images/1/url").asText().substring(PUBLIC_URL.length());

        mockMvc.perform(delete("/api/admin/catalog/variants/" + id + "/images/" + second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images[*].position", contains(1, 2)))
                .andExpect(jsonPath("$.images[1].url", containsString(".webp")));
        assertThat(DIRECTORY.resolve(secondName)).doesNotExist();
        assertThat(files()).hasSize(2);

        mockMvc.perform(delete("/api/admin/catalog/variants/" + id + "/images/" + second)).andExpect(status().isNotFound());
    }

    @Test
    void shouldReorderThePhotosTheFirstBecomingTheThumbnail() throws Exception {
        Long id = variantId("LOGI-G915TKL-AZ-NOI");
        upload(id, "a.jpg", JPEG);
        upload(id, "b.png", PNG);
        JsonNode variant = json(upload(id, "c.webp", WEBP));
        long a = variant.at("/images/0/id").asLong();
        long b = variant.at("/images/1/id").asLong();
        long c = variant.at("/images/2/id").asLong();

        reorder(id, "[" + c + ", " + a + ", " + b + "]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images[*].id", contains((int) c, (int) a, (int) b)))
                .andExpect(jsonPath("$.images[*].position", contains(1, 2, 3)));
        mockMvc.perform(get("/api/catalog/products/logitech-g915-tkl"))
                .andExpect(jsonPath("$.variants[0].images[0]", containsString(".webp")));

        reorder(id, "[" + c + ", " + a + "]").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("exactly once")));
        reorder(id, "[" + c + ", " + a + ", " + a + "]").andExpect(status().isBadRequest());
    }

    @Test
    void shouldDeleteTheFilesOfADeletedDraft() throws Exception {
        Long id = variantId("CORS-VENG-DDR5-32");
        upload(id, "a.jpg", JPEG);
        upload(id, "b.png", PNG);
        assertThat(files()).hasSize(2);

        mockMvc.perform(delete("/api/admin/catalog/products/" + jdbcTemplate.queryForObject(
                "SELECT id FROM produit WHERE slug = 'corsair-vengeance-rgb-ddr5'", Long.class)))
                .andExpect(status().isNoContent());
        assertThat(files()).isEmpty();
    }

    @Test
    void shouldAnswer404ForAnUnknownVariantOrImage() throws Exception {
        upload(999_999L, "a.jpg", JPEG).andExpect(status().isNotFound());
        assertThat(files()).isEmpty();
        mockMvc.perform(get("/api/catalog/images/0123456789abcdef0123456789abcdef.jpg")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/catalog/images/application.yml")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/catalog/images/..%2Fapplication.yml")).andExpect(status().is4xxClientError());
        mockMvc.perform(multipart("/api/admin/catalog/variants/" + variantId("LOGI-G915TKL-AZ-NOI") + "/images"))
                .andExpect(status().isBadRequest());
    }

    private ResultActions upload(Long variantId, String fileName, byte[] bytes) throws Exception {
        return mockMvc.perform(multipart("/api/admin/catalog/variants/" + variantId + "/images")
                .file(new MockMultipartFile("file", fileName, "image/jpeg", bytes)));
    }

    private ResultActions reorder(Long variantId, String ids) throws Exception {
        return mockMvc.perform(put("/api/admin/catalog/variants/" + variantId + "/images/order")
                .contentType(MediaType.APPLICATION_JSON).content("{\"imageIds\": " + ids + "}"));
    }

    private Long variantId(String sku) {
        return jdbcTemplate.queryForObject("SELECT id FROM variante WHERE sku = ?", Long.class, sku);
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsByteArray());
    }

    private static java.util.List<Path> files() throws IOException {
        try (Stream<Path> files = Files.list(DIRECTORY)) {
            return files.toList();
        }
    }

    private static byte[] bytes(byte[] start, String payload) {
        byte[] rest = payload.getBytes(StandardCharsets.US_ASCII);
        byte[] all = Arrays.copyOf(start, start.length + rest.length);
        System.arraycopy(rest, 0, all, start.length, rest.length);
        return all;
    }

    private static Path temporaryDirectory() {
        try {
            return Files.createTempDirectory("catalog-images-test");
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
