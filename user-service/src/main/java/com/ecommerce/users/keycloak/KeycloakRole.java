package com.ecommerce.users.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A realm role as the Admin API returns it. Mapping a role onto a user needs
 * its id as well as its name, so it is read back from Keycloak rather than
 * built locally.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakRole(String id, String name) {
}
