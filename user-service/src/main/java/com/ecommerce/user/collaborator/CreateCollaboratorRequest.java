package com.ecommerce.user.collaborator;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Creates a back-office account. The e-mail is also the login, and the
 * password is temporary: Keycloak makes the collaborator choose their own at
 * the first login. The realm password policy still applies to it.
 */
public record CreateCollaboratorRequest(
        @NotBlank(message = "email must not be blank")
        @Email(message = "email must be a valid e-mail address")
        @Size(max = 254, message = "email must be at most 254 characters")
        String email,

        @NotBlank(message = "firstName must not be blank")
        @Size(max = 100, message = "firstName must be at most 100 characters")
        String firstName,

        @NotBlank(message = "lastName must not be blank")
        @Size(max = 100, message = "lastName must be at most 100 characters")
        String lastName,

        @NotNull(message = "role must be 'collaborator' or 'admin'")
        CollaboratorRole role,

        @NotBlank(message = "temporaryPassword must not be blank")
        @Size(min = 8, max = 128, message = "temporaryPassword must be between 8 and 128 characters")
        String temporaryPassword) {
}
