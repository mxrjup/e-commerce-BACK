package com.ecommerce.user.collaborator;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Collection;
import java.util.Optional;

/**
 * The two back-office roles. Serialized as the name of the Keycloak realm role
 * itself, so the back-office reads the same value in the API and in the token.
 */
public enum CollaboratorRole {

    COLLABORATOR("collaborator"),
    /** Includes {@link #COLLABORATOR} through the Keycloak composite. */
    ADMIN("admin");

    private final String realmRole;

    CollaboratorRole(String realmRole) {
        this.realmRole = realmRole;
    }

    @JsonValue
    public String realmRole() {
        return realmRole;
    }

    /** The highest back-office role among the realm roles of a user, if any. */
    public static Optional<CollaboratorRole> highestOf(Collection<String> realmRoles) {
        if (realmRoles.contains(ADMIN.realmRole)) {
            return Optional.of(ADMIN);
        }
        if (realmRoles.contains(COLLABORATOR.realmRole)) {
            return Optional.of(COLLABORATOR);
        }
        return Optional.empty();
    }
}
