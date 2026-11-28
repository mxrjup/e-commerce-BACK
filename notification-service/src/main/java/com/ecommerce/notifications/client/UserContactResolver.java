package com.ecommerce.notifications.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Component
public class UserContactResolver {

    private final RestClient restClient;
    private final String internalReadKey;

    public UserContactResolver(
            RestClient.Builder restClientBuilder,
            @Value("${services.user-service.uri:http://localhost:8082}") String userServiceUri,
            @Value("${mcp.internal-read-key:}") String internalReadKey,
            @Value("${services.user-service.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${services.user-service.read-timeout-ms:5000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        this.restClient = restClientBuilder
                .baseUrl(userServiceUri)
                .requestFactory(requestFactory)
                .build();
        this.internalReadKey = internalReadKey;
    }

    public Optional<UserContact> resolveUserContact(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }

        try {
            UserContact response = restClient.get()
                    .uri("/api/internal/users/{id}/contact", userId)
                    .header("X-MCP-Service-Key", internalReadKey)
                    .retrieve()
                    .body(UserContact.class);

            if (response == null) {
                log.warn("Réponse vide de user-service pour userId={}", userId);
                return Optional.empty();
            }
            return Optional.of(response);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.warn("Utilisateur introuvable dans user-service (userId={}), notification ignorée.", userId);
                return Optional.empty();
            }
            throw e;
        }
    }

    public record UserContact(Long id, String subject, String email, String firstName) {
        public boolean hasSubject() {
            return subject != null && !subject.isBlank();
        }

        public boolean hasEmail() {
            return email != null && !email.isBlank();
        }
    }
}