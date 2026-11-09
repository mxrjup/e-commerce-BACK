package com.ecommerce.user.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Obtains the access token of the service account with the client
 * credentials grant, and reuses it until shortly before it expires.
 *
 * <p>No refresh token is involved: Keycloak does not issue one for this grant,
 * a new token is simply requested.
 */
@Component
public class KeycloakTokenProvider {

    /** Renewed this long before expiry, so a token never expires mid-request. */
    static final Duration EXPIRY_MARGIN = Duration.ofSeconds(30);

    private final RestClient restClient;
    private final KeycloakAdminProperties properties;
    private final Clock clock;

    private volatile CachedToken cached;

    public KeycloakTokenProvider(RestClient keycloakRestClient, KeycloakAdminProperties properties, Clock clock) {
        this.restClient = keycloakRestClient;
        this.properties = properties;
        this.clock = clock;
    }

    public String accessToken() {
        CachedToken current = cached;
        if (current != null && current.isValidAt(clock.instant())) {
            return current.value();
        }
        synchronized (this) {
            current = cached;
            if (current == null || !current.isValidAt(clock.instant())) {
                current = requestToken();
                cached = current;
            }
            return current.value();
        }
    }

    /** Forgets the token, after Keycloak rejected it (revoked session, secret rotated). */
    public void invalidate() {
        cached = null;
    }

    private CachedToken requestToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());

        TokenResponse response;
        try {
            response = restClient.post()
                    .uri("/realms/{realm}/protocol/openid-connect/token", properties.realm())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
        } catch (RestClientException e) {
            // Le message de Keycloak n'est pas repris : il peut citer le client.
            throw new KeycloakUnavailableException("Could not authenticate against Keycloak", e);
        }
        if (response == null || response.accessToken() == null) {
            throw new KeycloakUnavailableException("Keycloak returned no access token");
        }
        Instant expiresAt = clock.instant().plusSeconds(response.expiresIn()).minus(EXPIRY_MARGIN);
        return new CachedToken(response.accessToken(), expiresAt);
    }

    private record CachedToken(String value, Instant expiresAt) {

        boolean isValidAt(Instant instant) {
            return instant.isBefore(expiresAt);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") long expiresIn) {
    }
}
