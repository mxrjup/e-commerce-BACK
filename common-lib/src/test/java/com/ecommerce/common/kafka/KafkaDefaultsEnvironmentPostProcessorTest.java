package com.ecommerce.common.kafka;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaDefaultsEnvironmentPostProcessorTest {

    private final KafkaDefaultsEnvironmentPostProcessor postProcessor = new KafkaDefaultsEnvironmentPostProcessor();

    @Test
    void shouldProvideSharedKafkaDefaults() {
        StandardEnvironment environment = environmentWith(Map.of("spring.application.name", "cart-service"));

        postProcessor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.kafka.producer.value-serializer"))
                .isEqualTo("org.springframework.kafka.support.serializer.JsonSerializer");
        assertThat(environment.getProperty("spring.kafka.consumer.value-deserializer"))
                .isEqualTo("org.springframework.kafka.support.serializer.ErrorHandlingDeserializer");
        assertThat(environment.getProperty("spring.kafka.consumer.properties.spring.json.trusted.packages"))
                .isEqualTo("com.ecommerce.common.event");
        assertThat(environment.getProperty("spring.kafka.consumer.group-id")).isEqualTo("cart-service");
        assertThat(environment.getProperty("app.kafka.topics.order-paid")).isEqualTo(KafkaTopics.ORDER_PAID);
        assertThat(environment.getProperty("app.kafka.topics.catalog-events")).isEqualTo(KafkaTopics.CATALOG_EVENTS);
        assertThat(environment.getProperty("app.kafka.topics.support-admin-reply"))
                .isEqualTo(KafkaTopics.SUPPORT_ADMIN_REPLY);
    }

    @Test
    void shouldLetTheServiceConfigurationWin() {
        StandardEnvironment environment = environmentWith(Map.of(
                "spring.kafka.bootstrap-servers", "kafka:9092",
                "app.kafka.topics.order-paid", "order.paid.staging"));

        postProcessor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.kafka.bootstrap-servers")).isEqualTo("kafka:9092");
        assertThat(environment.getProperty("app.kafka.topics.order-paid")).isEqualTo("order.paid.staging");
    }

    @Test
    void shouldBeIdempotent() {
        StandardEnvironment environment = new StandardEnvironment();

        postProcessor.postProcessEnvironment(environment, new SpringApplication());
        postProcessor.postProcessEnvironment(environment, new SpringApplication());

        long sources = environment.getPropertySources().stream()
                .filter(source -> source.getName().equals(KafkaDefaultsEnvironmentPostProcessor.PROPERTY_SOURCE_NAME))
                .count();
        assertThat(sources).isEqualTo(1);
    }

    private static StandardEnvironment environmentWith(Map<String, Object> properties) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("service", properties));
        return environment;
    }
}
