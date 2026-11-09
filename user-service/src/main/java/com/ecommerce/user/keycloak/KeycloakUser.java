package com.ecommerce.user.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The fields of a Keycloak {@code UserRepresentation} the service reads.
 *
 * @param createdTimestamp creation date, in milliseconds since the epoch
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakUser(
        String id,
        String username,
        String email,
        String firstName,
        String lastName,
        boolean enabled,
        Long createdTimestamp) {
}
