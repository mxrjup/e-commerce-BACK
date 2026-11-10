package com.ecommerce.mcp.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeedShipmentToolsTest {
    @Test
    void countsOnlyRemainingPreparationUnitsAndDistinguishesMissingProducts() throws Exception {
        var tools = new SeedShipmentTools(new ObjectMapper(), new ClassPathResource("seed/product-shipments.json"));
        var found = tools.getPendingShipments("  logitech g pro  ");
        assertThat(found.status()).isEqualTo("FOUND");
        var product = found.matches().get(0);
        assertThat(product.pendingUnits()).isEqualTo(7);
        assertThat(product.pendingOrderCount()).isEqualTo(2);
        assertThat(product.orders()).extracting(SeedShipmentTools.PendingOrder::remainingUnits).containsExactly(3, 4);
        assertThat(tools.getPendingShipments("Logitech").status()).isEqualTo("MULTIPLE_MATCHES");
        var zero = tools.getPendingShipments("DeathAdder");
        assertThat(zero.status()).isEqualTo("FOUND");
        assertThat(zero.matches().get(0).pendingUnits()).isZero();
        assertThat(zero.matches().get(0).orders()).isEmpty();
        assertThat(tools.getPendingShipments("unknown").status()).isEqualTo("NOT_FOUND");
        assertThat(tools.getPendingShipments("unknown").matches()).isEmpty();
        assertThatThrownBy(() -> tools.getPendingShipments(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tools.getPendingShipments("x".repeat(201))).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "null", "[null]",
            "[{\"orderId\":\"O\",\"sku\":\"A\",\"productName\":\"Mouse\",\"quantity\":0,\"shippedQuantity\":0,\"status\":\"NEW\"}]",
            "[{\"orderId\":\"O\",\"sku\":\"A\",\"productName\":\"Mouse\",\"quantity\":1,\"shippedQuantity\":2,\"status\":\"IN_PREPARATION\"}]",
            "[{\"orderId\":\"O\",\"sku\":\"A\",\"productName\":\"Mouse\",\"quantity\":1,\"shippedQuantity\":0}]"
    })
    void rejectsInvalidSeed(String seed) {
        assertThatThrownBy(() -> new SeedShipmentTools(new ObjectMapper(),
                new ByteArrayResource(seed.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
