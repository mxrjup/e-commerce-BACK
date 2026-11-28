package com.ecommerce.users.keycloak;

import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.common.exception.ConflictException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The handful of Admin API endpoints the service needs, on the realm of
 * {@link KeycloakAdminProperties}.
 *
 * <p>Errors are translated once, here: a user that does not exist becomes an
 * empty result, a duplicate a {@link ConflictException}, a rejected value (the
 * password policy) a {@link BadRequestException} carrying Keycloak's message,
 * and anything else a {@link KeycloakUnavailableException}.
 */
@Component
public class KeycloakAdminClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeycloakAdminClient.class);

    static final int PAGE_SIZE = 100;

    private static final ObjectMapper ERROR_READER = new ObjectMapper();

    private static final ParameterizedTypeReference<List<KeycloakUser>> USERS = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<List<KeycloakRole>> ROLES = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<Map<String, Object>> REPRESENTATION =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final KeycloakTokenProvider tokenProvider;
    private final String realm;

    public KeycloakAdminClient(RestClient keycloakRestClient, KeycloakTokenProvider tokenProvider,
                               KeycloakAdminProperties properties) {
        this.tokenProvider = tokenProvider;
        this.realm = properties.realm();
        this.restClient = keycloakRestClient.mutate()
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().setBearerAuth(tokenProvider.accessToken());
                    return execution.execute(request, body);
                })
                .defaultStatusHandler(HttpStatusCode::isError, this::translateError)
                .build();
    }

    /** Every user holding the realm role directly, not through a group or a composite. */
    public List<KeycloakUser> usersWithRealmRole(String role) {
        List<KeycloakUser> users = new ArrayList<>();
        int first = 0;
        List<KeycloakUser> page;
        do {
            int offset = first;
            page = guard(() -> restClient.get()
                    .uri("/admin/realms/{realm}/roles/{role}/users?first={first}&max={max}&briefRepresentation=false",
                            realm, role, offset, PAGE_SIZE)
                    .retrieve()
                    .body(USERS));
            if (page == null) {
                break;
            }
            users.addAll(page);
            first += PAGE_SIZE;
        } while (page.size() == PAGE_SIZE);
        return users;
    }

    public Optional<KeycloakUser> findUser(String userId) {
        return guard(() -> restClient.get()
                .uri("/admin/realms/{realm}/users/{id}", realm, userId)
                .exchange((request, response) -> response.getStatusCode().value() == HttpStatus.NOT_FOUND.value()
                        ? Optional.empty()
                        : Optional.ofNullable(readUser(response))));
    }

    /** The names of the realm roles mapped directly onto the user. */
    public List<String> realmRoleNames(String userId) {
        List<KeycloakRole> roles = guard(() -> restClient.get()
                .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", realm, userId)
                .retrieve()
                .body(ROLES));
        return roles == null ? List.of() : roles.stream().map(KeycloakRole::name).toList();
    }

    /** Creates the user and returns its id, read from the {@code Location} Keycloak answers with. */
    public String createUser(NewKeycloakUser user) {
        URI location = guard(() -> restClient.post()
                .uri("/admin/realms/{realm}/users", realm)
                .body(user)
                .retrieve()
                .toBodilessEntity()
                .getHeaders()
                .getLocation());
        if (location == null) {
            throw new KeycloakUnavailableException("Keycloak created the user without returning its location");
        }
        String path = location.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    public void deleteUser(String userId) {
        guard(() -> restClient.delete()
                .uri("/admin/realms/{realm}/users/{id}", realm, userId)
                .retrieve()
                .toBodilessEntity());
    }

    public void addRealmRoles(String userId, List<String> roleNames) {
        List<KeycloakRole> roles = roleNames.stream().map(this::realmRole).toList();
        guard(() -> restClient.post()
                .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", realm, userId)
                .body(roles)
                .retrieve()
                .toBodilessEntity());
    }

    public void removeRealmRoles(String userId, List<String> roleNames) {
        List<KeycloakRole> roles = roleNames.stream().map(this::realmRole).toList();
        // DELETE avec un corps : c'est ainsi que l'Admin API retire des roles.
        guard(() -> restClient.method(HttpMethod.DELETE)
                .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", realm, userId)
                .body(roles)
                .retrieve()
                .toBodilessEntity());
    }

    /**
     * Enables or disables the account. The full representation is read and
     * written back: a partial one would let Keycloak reset the profile
     * attributes it does not contain.
     */
    public void setEnabled(String userId, boolean enabled) {
        Map<String, Object> representation = guard(() -> restClient.get()
                .uri("/admin/realms/{realm}/users/{id}", realm, userId)
                .retrieve()
                .body(REPRESENTATION));
        if (representation == null) {
            throw new KeycloakUnavailableException("Keycloak returned an empty user");
        }
        representation.put("enabled", enabled);
        guard(() -> restClient.put()
                .uri("/admin/realms/{realm}/users/{id}", realm, userId)
                .body(representation)
                .retrieve()
                .toBodilessEntity());
    }

    /** Ends every session of the user: a disabled account must not keep a valid refresh token. */
    public void logout(String userId) {
        guard(() -> restClient.post()
                .uri("/admin/realms/{realm}/users/{id}/logout", realm, userId)
                .retrieve()
                .toBodilessEntity());
    }

    private KeycloakRole realmRole(String name) {
        KeycloakRole role = guard(() -> restClient.get()
                .uri("/admin/realms/{realm}/roles/{role}", realm, name)
                .retrieve()
                .body(KeycloakRole.class));
        if (role == null) {
            throw new KeycloakUnavailableException("Keycloak returned no role named '" + name + "'");
        }
        return role;
    }

    private KeycloakUser readUser(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response)
            throws IOException {
        if (response.getStatusCode().isError()) {
            translateError(null, response);
        }
        return response.bodyTo(KeycloakUser.class);
    }

    private void translateError(HttpRequest request, ClientHttpResponse response)
            throws IOException {
        HttpStatus status = HttpStatus.resolve(response.getStatusCode().value());
        String message = readErrorMessage(response);
        if (status == HttpStatus.UNAUTHORIZED) {
            tokenProvider.invalidate();
        }
        if (status == HttpStatus.CONFLICT) {
            throw new ConflictException(message != null ? message : "The account conflicts with an existing one");
        }
        if (status == HttpStatus.BAD_REQUEST && message != null) {
            throw new BadRequestException(message);
        }
        LOGGER.warn("Keycloak Admin API answered {}{}", response.getStatusCode().value(),
                message != null ? ": " + message : "");
        throw new KeycloakUnavailableException("Keycloak Admin API answered " + response.getStatusCode().value());
    }

    /** Keycloak puts a readable reason in {@code errorMessage} or {@code error_description}. */
    private static String readErrorMessage(ClientHttpResponse response) {
        try {
            byte[] body = response.getBody().readAllBytes();
            if (body.length == 0) {
                return null;
            }
            KeycloakError error = ERROR_READER.readValue(body, KeycloakError.class);
            return error.errorMessage() != null ? error.errorMessage() : error.errorDescription();
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** I/O failures (connection refused, timeout) are an unavailable Keycloak too. */
    private static <T> T guard(Supplier<T> call) {
        try {
            return call.get();
        } catch (ConflictException | BadRequestException | KeycloakUnavailableException e) {
            throw e;
        } catch (HttpClientErrorException e) {
            throw new KeycloakUnavailableException("Keycloak Admin API answered " + e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            throw new KeycloakUnavailableException("Keycloak Admin API is unreachable", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KeycloakError(
            String errorMessage,
            @JsonProperty("error_description") String errorDescription) {
    }
}
