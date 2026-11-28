package com.ecommerce.users.keycloak;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Where the Admin API of Keycloak lives and the confidential client the
 * service authenticates with.
 *
 * @param serverUrl    base URL of Keycloak reachable from the service, without
 *                     {@code /realms/...}; it may differ from the issuer the
 *                     browsers see
 * @param realm        realm whose users are managed
 * @param clientId     confidential client holding the service account
 * @param clientSecret secret of that client, always injected from the
 *                     environment
 */
@Validated
@ConfigurationProperties("keycloak.admin")
public record KeycloakAdminProperties(
        @NotBlank String serverUrl,
        @NotBlank String realm,
        @NotBlank String clientId,
        @NotBlank String clientSecret) {
}
