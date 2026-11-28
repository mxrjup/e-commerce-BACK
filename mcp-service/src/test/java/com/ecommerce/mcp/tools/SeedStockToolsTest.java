package com.ecommerce.mcp.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeedStockToolsTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "null",
            "[null]",
            "[{\"sku\":\"A\",\"name\":\"Mouse\",\"stock\":-1}]",
            "[{\"sku\":\"A\",\"name\":\"Mouse\"}]",
            "[{\"sku\":\"A\",\"name\":\" \",\"stock\":1}]",
            "[{\"sku\":\"A\",\"name\":\"Mouse\",\"stock\":1},{\"sku\":\"A\",\"name\":\"Keyboard\",\"stock\":2}]"
    })
    void shouldRejectInvalidSeedData(String seed) {
        assertThatThrownBy(() -> new SeedStockTools(new ObjectMapper(),
                new ByteArrayResource(seed.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
