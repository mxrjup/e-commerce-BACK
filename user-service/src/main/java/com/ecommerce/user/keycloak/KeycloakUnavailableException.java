package com.ecommerce.user.keycloak;

/**
 * Keycloak could not be reached or answered in a way the service cannot act
 * on. Mapped to HTTP 502: the request was fine, the identity provider was not.
 */
public class KeycloakUnavailableException extends RuntimeException {

    public KeycloakUnavailableException(String message) {
        super(message);
    }

    public KeycloakUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
