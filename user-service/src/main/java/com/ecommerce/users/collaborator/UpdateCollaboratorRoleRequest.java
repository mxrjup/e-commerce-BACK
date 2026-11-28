package com.ecommerce.users.collaborator;

import jakarta.validation.constraints.NotNull;

public record UpdateCollaboratorRoleRequest(
        @NotNull(message = "role must be 'collaborator' or 'admin'")
        CollaboratorRole role) {
}
