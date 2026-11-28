package com.ecommerce.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The metrics of the service for Prometheus (D06): served without a token,
 * tagged with the service name, with the HTTP response times as a histogram.
 * {@code @AutoConfigureObservability} because Spring Boot switches the
 * metrics export off in tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability
class MetricsEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldServeTheMetricsWithoutAToken() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("application=\"catalog-service\"")))
                .andExpect(content().string(containsString("http_server_requests_seconds_bucket")))
                .andExpect(content().string(containsString("jvm_memory_used_bytes")));
    }
}
