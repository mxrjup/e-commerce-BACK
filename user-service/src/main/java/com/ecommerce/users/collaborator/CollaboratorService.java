package com.ecommerce.users.collaborator;

import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.users.keycloak.KeycloakAdminClient;
import com.ecommerce.users.keycloak.KeycloakUser;
import com.ecommerce.users.keycloak.NewKeycloakUser;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Back-office accounts, kept in Keycloak and nowhere else: Keycloak is the
 * only source of truth for who may log in and with which role.
 *
 * <p>A collaborator is an account holding the {@code collaborator} or the
 * {@code admin} realm role directly. Every other account (the shop customers)
 * is invisible here: this API can neither read nor promote them.
 */
@Service
@RequiredArgsConstructor
public class CollaboratorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CollaboratorService.class);

    private static final Comparator<CollaboratorResponse> BY_EMAIL =
            Comparator.comparing(CollaboratorResponse::email, Comparator.nullsLast(String::compareToIgnoreCase));

    private final KeycloakAdminClient keycloak;

    public List<CollaboratorResponse> list() {
        // Les admins d'abord : un compte qui porte les deux roles est un admin.
        Map<String, CollaboratorResponse> byId = new LinkedHashMap<>();
        for (CollaboratorRole role : List.of(CollaboratorRole.ADMIN, CollaboratorRole.COLLABORATOR)) {
            for (KeycloakUser user : keycloak.usersWithRealmRole(role.realmRole())) {
                byId.putIfAbsent(user.id(), CollaboratorResponse.from(user, role));
            }
        }
        return byId.values().stream().sorted(BY_EMAIL).toList();
    }

    public CollaboratorResponse get(String id) {
        KeycloakUser user = keycloak.findUser(id).orElseThrow(() -> notFound(id));
        return CollaboratorResponse.from(user, roleOf(id));
    }

    public CollaboratorResponse create(CreateCollaboratorRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String id = keycloak.createUser(NewKeycloakUser.withTemporaryPassword(
                email, request.firstName().trim(), request.lastName().trim(), request.temporaryPassword()));
        try {
            keycloak.addRealmRoles(id, List.of(request.role().realmRole()));
        } catch (RuntimeException e) {
            // Sans son role, le compte ne serait ni un collaborateur ni visible
            // ici : on ne le laisse pas trainer dans Keycloak.
            deleteQuietly(id);
            throw e;
        }
        return get(id);
    }

    public CollaboratorResponse changeRole(String id, CollaboratorRole role, String actorId) {
        refuseOnOwnAccount(id, actorId, "change your own role");
        CollaboratorResponse current = get(id);
        if (current.role() == role) {
            return current;
        }
        // Le nouveau role avant le retrait de l'ancien : le compte ne perd
        // jamais l'acces au back-office, meme si le second appel echoue.
        keycloak.addRealmRoles(id, List.of(role.realmRole()));
        keycloak.removeRealmRoles(id, List.of(current.role().realmRole()));
        return get(id);
    }

    public CollaboratorResponse disable(String id, String actorId) {
        refuseOnOwnAccount(id, actorId, "disable your own account");
        get(id);
        keycloak.setEnabled(id, false);
        keycloak.logout(id);
        return get(id);
    }

    public CollaboratorResponse enable(String id) {
        get(id);
        keycloak.setEnabled(id, true);
        return get(id);
    }

    private CollaboratorRole roleOf(String id) {
        return CollaboratorRole.highestOf(keycloak.realmRoleNames(id)).orElseThrow(() -> notFound(id));
    }

    /**
     * An administrator demoting or disabling themselves could leave the shop
     * without any administrator, with no way back but the Keycloak console.
     */
    private static void refuseOnOwnAccount(String id, String actorId, String action) {
        if (actorId != null && Objects.equals(id, actorId)) {
            throw new ConflictException("You cannot " + action);
        }
    }

    private void deleteQuietly(String id) {
        try {
            keycloak.deleteUser(id);
        } catch (RuntimeException e) {
            LOGGER.error("Account {} was created without its role and could not be deleted", id, e);
        }
    }

    private static ResourceNotFoundException notFound(String id) {
        return new ResourceNotFoundException("Collaborator " + id + " not found");
    }
}
