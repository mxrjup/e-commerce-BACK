package com.ecommerce.user.keycloak;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;

@Configuration
public class KeycloakClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    /**
     * The one HTTP client towards Keycloak, shared by the token request and
     * the Admin API calls. Built from the Boot builder so that its JSON
     * converters, and the mock server of the tests, apply.
     */
    @Bean
    RestClient keycloakRestClient(RestClient.Builder builder, KeycloakAdminProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return builder
                .requestFactory(requestFactory)
                .baseUrl(properties.serverUrl())
                .build();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
