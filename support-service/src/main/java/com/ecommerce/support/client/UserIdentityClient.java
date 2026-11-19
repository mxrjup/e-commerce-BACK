package com.ecommerce.support.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Component
public class UserIdentityClient {

    private static final String HEADER_INTERNAL_KEY = "X-Internal-Key";

    private final RestClient restClient;
    private final String internalReadKey;

    public UserIdentityClient(
            RestClient.Builder restClientBuilder,
            @Value("${services.user-service.uri:http://localhost:8082}") String userServiceUri,
            @Value("${mcp.internal-read-key:}") String internalReadKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = restClientBuilder
                .baseUrl(userServiceUri)
                .requestFactory(requestFactory)
                .build();
        this.internalReadKey = internalReadKey;
    }

    public Optional<Long> resolveUserIdBySubject(String subject) {
        if (subject == null || subject.isBlank() || internalReadKey == null || internalReadKey.isBlank()) {
            return Optional.empty();
        }
        try {
            UserContactPayload response = restClient.get()
                    .uri("/api/internal/users/by-subject/{subject}", subject)
                    .header(HEADER_INTERNAL_KEY, internalReadKey)
                    .retrieve()
                    .body(UserContactPayload.class);
            return response != null ? Optional.ofNullable(response.id()) : Optional.empty();
        } catch (RestClientException e) {
            log.warn("Impossible de résoudre userId pour subject={} : {}", subject, e.getMessage());
            return Optional.empty();
        }
    }

    private record UserContactPayload(Long id, String subject, String email, String firstName) {
    }
}