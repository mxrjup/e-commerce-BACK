package com.ecommerce.notifications.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Slf4j
@Component
public class UserSubjectResolver {

    private final RestClient restClient;
    private final String internalReadKey;

    public UserSubjectResolver(
            RestClient.Builder restClientBuilder,
            @Value("${services.user-service.uri:http://localhost:8082}") String userServiceUri,
            @Value("${mcp.internal-read-key:}") String internalReadKey) {
        this.restClient = restClientBuilder.baseUrl(userServiceUri).build();
        this.internalReadKey = internalReadKey;
    }

    public Optional<String> resolveKeycloakSubject(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }

        try {
            UserSubjectResponse response = restClient.get()
                    .uri("/api/internal/users/{id}/subject", userId)
                    .header("X-MCP-Service-Key", internalReadKey)
                    .retrieve()
                    .body(UserSubjectResponse.class);

            if (response == null || response.subject() == null || response.subject().isBlank()) {
                log.warn("Aucun subject Keycloak trouvé pour userId={}", userId);
                return Optional.empty();
            }
            return Optional.of(response.subject());
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.warn("Utilisateur introuvable dans user-service (userId={}), notification ignorée.", userId);
                return Optional.empty();
            }
            throw e;
        }
    }

    public record UserSubjectResponse(Long id, String subject) {
    }
}