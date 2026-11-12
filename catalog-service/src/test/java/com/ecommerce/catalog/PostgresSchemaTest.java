package com.ecommerce.catalog;

import com.ecommerce.catalog.repository.ProductRepository;
import com.ecommerce.catalog.support.DockerRequiredInCi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The migrations, the schema validation and the demonstration data on the
 * PostgreSQL the service runs on. The other tests use H2, which accepts
 * column types that Hibernate refuses on PostgreSQL: without this test, the
 * build could pass while the service fails to start.
 */
@ExtendWith(DockerRequiredInCi.class)
@SpringBootTest
@ActiveProfiles("demo")
class PostgresSchemaTest {

    /** Same major version as the Docker Compose stack. Stopped by Testcontainers when the JVM exits. */
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        POSTGRES.start();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldMigrateValidateAndLoadTheDemoCatalogue() {
        // Le contexte a demarre : Flyway a joue V1 et V2, et Hibernate a valide le schema.
        assertThat(jdbcTemplate.queryForObject("SELECT MAX(version) FROM flyway_schema_history", String.class))
                .isEqualTo("2");
        assertThat(productRepository.count()).isEqualTo(19);
    }

    @Test
    void shouldRefuseOverselling() {
        Long id = jdbcTemplate.queryForObject("SELECT id FROM variante WHERE sku = 'SAMS-ODG7-27'", Long.class);

        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE variante SET stock = stock - 6 WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT stock FROM variante WHERE id = ?", Integer.class, id))
                .isEqualTo(5);
    }
}
