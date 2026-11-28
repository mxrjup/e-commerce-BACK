package com.ecommerce.users.collaborator;

import com.ecommerce.users.keycloak.KeycloakUser;

import java.time.Instant;

/**
 * @param id the Keycloak id of the account, the {@code sub} of its tokens
 */
public record CollaboratorResponse(
        String id,
        String email,
        String firstName,
        String lastName,
        CollaboratorRole role,
        boolean enabled,
        Instant createdAt) {

    static CollaboratorResponse from(KeycloakUser user, CollaboratorRole role) {
        Instant createdAt = user.createdTimestamp() == null ? null : Instant.ofEpochMilli(user.createdTimestamp());
        return new CollaboratorResponse(user.id(), user.email(), user.firstName(), user.lastName(), role,
                user.enabled(), createdAt);
    }
}
