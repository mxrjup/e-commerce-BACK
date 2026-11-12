package com.ecommerce.catalog.demo;

import com.ecommerce.catalog.entity.AttributeValue;
import com.ecommerce.catalog.entity.Product;
import com.ecommerce.catalog.entity.ProductStatus;
import com.ecommerce.catalog.entity.Variant;
import com.ecommerce.catalog.repository.ProductRepository;
import com.ecommerce.catalog.repository.VariantRepository;
import com.ecommerce.catalog.support.CatalogTestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The demonstration catalogue, loaded at startup with the {@code demo}
 * profile, in a database of its own so the other tests never see it.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:catalogdemo;MODE=PostgreSQL;"
        + "DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("demo")
class DemoCatalogLoaderTest {

    @Autowired
    private DemoCatalogLoader loader;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private VariantRepository variantRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transaction;

    /** Loaded at startup for the first test, again after each clean-up for the next ones. */
    @BeforeEach
    void loadIfEmpty() {
        loader.run(null);
    }

    @AfterEach
    void tearDown() {
        CatalogTestData.clear(jdbcTemplate);
    }

    @Test
    void shouldFillAnEmptyCatalogueAndNeverTwice() {
        assertThat(productRepository.count()).isEqualTo(19);
        assertThat(variantRepository.count()).isEqualTo(30);

        loader.run(null);

        assertThat(productRepository.count()).isEqualTo(19);
        assertThat(count("marque")).isEqualTo(11);
        assertThat(count("categorie")).isEqualTo(10);
        assertThat(count("univers")).isEqualTo(4);
    }

    @Test
    void shouldLoadUsableProducts() {
        transaction.executeWithoutResult(status -> {
            Variant g915 = variantRepository.findBySku("LOGI-G915TKL-AZ-NOI").orElseThrow();
            assertThat(g915.getProduct().getTitle()).isEqualTo("Logitech G915 TKL");
            assertThat(g915.getProduct().getCategory().getParent().getSlug()).isEqualTo("peripheriques");
            assertThat(g915.getSalePriceExclTax()).isEqualByComparingTo("191.66");
            assertThat(g915.getDiscountPriceExclTax()).isEqualByComparingTo("166.66");
            assertThat(g915.getValues()).extracting(AttributeValue::getValue)
                    .containsExactlyInAnyOrder("AZERTY", "Noir");

            Product draft = productRepository.findBySlug("corsair-vengeance-rgb-ddr5").orElseThrow();
            assertThat(draft.getStatus()).isEqualTo(ProductStatus.BROUILLON);
        });

        // De quoi tester la vitrine et l'inventaire : ruptures et stocks bas.
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM variante WHERE stock = 0", Long.class))
                .isPositive();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM variante WHERE stock > 0 AND stock <= seuil_alerte", Long.class)).isPositive();
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }
}
