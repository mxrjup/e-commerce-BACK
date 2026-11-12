package com.ecommerce.users.keycloak;

import java.util.List;

/**
 * The body of a user creation on the Admin API.
 *
 * <p>The e-mail is trusted, since an administrator typed it, and the
 * password is temporary: Keycloak asks for a new one at the first login.
 */
public record NewKeycloakUser(
        String username,
        String email,
        String firstName,
        String lastName,
        boolean enabled,
        boolean emailVerified,
        List<Credential> credentials) {

    public static NewKeycloakUser withTemporaryPassword(String email, String firstName, String lastName,
                                                        String temporaryPassword) {
        return new NewKeycloakUser(email, email, firstName, lastName, true, true,
                List.of(new Credential("password", temporaryPassword, true)));
    }

    public record Credential(String type, String value, boolean temporary) {
    }
}
