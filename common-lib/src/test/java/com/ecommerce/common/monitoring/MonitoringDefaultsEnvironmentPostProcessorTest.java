package com.ecommerce.common.monitoring;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class MonitoringDefaultsEnvironmentPostProcessorTest {

    private final MonitoringDefaultsEnvironmentPostProcessor postProcessor =
            new MonitoringDefaultsEnvironmentPostProcessor();

    @Test
    void shouldExposeTheMetricsTaggedWithTheServiceName() {
        StandardEnvironment environment = environmentWith(Map.of("spring.application.name", "catalog-service"));

        postProcessor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("management.endpoints.web.exposure.include"))
                .isEqualTo("health,info,prometheus");
        assertThat(environment.getProperty("management.endpoint.health.probes.enabled")).isEqualTo("true");
        assertThat(environment.getProperty("management.metrics.tags.application")).isEqualTo("catalog-service");
        assertThat(environment.getProperty(
                "management.metrics.distribution.percentiles-histogram.http.server.requests")).isEqualTo("true");
    }

    @Test
    void shouldLetTheServiceConfigurationWin() {
        StandardEnvironment environment = environmentWith(Map.of(
                "management.endpoints.web.exposure.include", "health"));

        postProcessor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health");
    }

    @Test
    void shouldBeIdempotent() {
        StandardEnvironment environment = new StandardEnvironment();

        postProcessor.postProcessEnvironment(environment, new SpringApplication());
        postProcessor.postProcessEnvironment(environment, new SpringApplication());

        long sources = environment.getPropertySources().stream()
                .filter(source -> source.getName().equals(MonitoringDefaultsEnvironmentPostProcessor.PROPERTY_SOURCE_NAME))
                .count();
        assertThat(sources).isEqualTo(1);
    }

    private static StandardEnvironment environmentWith(Map<String, Object> properties) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("service", properties));
        return environment;
    }
}
