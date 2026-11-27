package com.ecommerce.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The metrics of the gateway for Prometheus (D06): served without a token,
 * tagged with the service name. {@code @AutoConfigureObservability} because
 * Spring Boot switches the metrics export off in tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureObservability
class GatewayMetricsTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private ReactiveJwtDecoder jwtDecoder;

    @Test
    void shouldServeTheMetricsWithoutAToken() {
        webTestClient.get().uri("/actuator/health").exchange().expectStatus().isOk();

        String metrics = webTestClient.get().uri("/actuator/prometheus").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();

        assertThat(metrics)
                .contains("application=\"api-gateway\"")
                .contains("http_server_requests_seconds_bucket")
                .contains("jvm_memory_used_bytes");
    }

    @Test
    void shouldKeepTheOtherActuatorEndpointsClosed() {
        // Seuls health, info et prometheus sont exposes : env ou beans repondent 404, et sans jeton 401.
        webTestClient.get().uri("/actuator/env").exchange().expectStatus().value(status ->
                assertThat(status).isIn(401, 404));
    }
}
