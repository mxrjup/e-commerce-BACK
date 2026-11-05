package com.ecommerce.common.kafka;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.util.ClassUtils;

/**
 * Gives every Kafka service the same {@code spring.kafka.*} defaults: JSON
 * serialization of the shared events, a deserializer that does not loop on a
 * malformed record, the consumer group named after the service, and the
 * {@code app.kafka.topics.*} names.
 *
 * <p>The defaults are added with the lowest precedence: anything set in a
 * service's {@code application.yml}, an environment variable or a test
 * property wins. Nothing is added to a service without spring-kafka.
 */
public class KafkaDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String PROPERTY_SOURCE_NAME = "ecommerceKafkaDefaults";
    static final String DEFAULTS_LOCATION = "com/ecommerce/common/kafka/kafka-defaults.properties";

    private static final String KAFKA_MARKER_CLASS = "org.springframework.kafka.core.KafkaTemplate";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!ClassUtils.isPresent(KAFKA_MARKER_CLASS, getClass().getClassLoader())
                || environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            return;
        }
        try {
            ClassPathResource defaults = new ClassPathResource(DEFAULTS_LOCATION, getClass().getClassLoader());
            environment.getPropertySources().addLast(new ResourcePropertySource(PROPERTY_SOURCE_NAME, defaults));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + DEFAULTS_LOCATION, e);
        }
    }
}
