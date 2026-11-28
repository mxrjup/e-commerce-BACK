package com.ecommerce.common.monitoring;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;

/**
 * Gives every service the same supervision settings (D06): the health,
 * info and prometheus endpoints, Kubernetes probes, an {@code application}
 * tag on every metric, and a histogram of the HTTP response times.
 *
 * <p>Like {@code KafkaDefaultsEnvironmentPostProcessor}, the defaults are
 * added with the lowest precedence: a service's {@code application.yml}, an
 * environment variable or a test property wins.
 */
public class MonitoringDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String PROPERTY_SOURCE_NAME = "ecommerceMonitoringDefaults";
    static final String DEFAULTS_LOCATION = "com/ecommerce/common/monitoring/monitoring-defaults.properties";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
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
