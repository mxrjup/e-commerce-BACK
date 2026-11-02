package com.ecommerce.cart.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KafkaConsumerConfigTest {

    @Test
    @DisplayName("Kafka consumer config relies on the shared OrderPaidEvent contract")
    void kafkaConsumerUsesSharedOrderPaidType() throws IOException {
        String config = Files.readString(Path.of("src/main/resources/application.yml"));

        assertFalse(config.contains("spring.json.use.type.headers: false"));
        assertFalse(config.contains("spring.json.value.default.type:"));
        assertTrue(config.contains("spring.json.trusted.packages: \"*\""));
    }
}
