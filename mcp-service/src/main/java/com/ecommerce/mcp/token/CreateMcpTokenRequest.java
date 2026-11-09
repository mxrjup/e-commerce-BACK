package com.ecommerce.mcp.token;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;

/**
 * @param name      what the token is for, e.g. « Claude Desktop - Marius »
 * @param expiresAt optional: without it, the token lives until revoked
 */
public record CreateMcpTokenRequest(
        @NotBlank(message = "name must not be blank")
        @Size(max = 100, message = "name must be at most 100 characters")
        String name,

        @NotEmpty(message = "scopes must contain at least one scope")
        Set<McpScope> scopes,

        @Future(message = "expiresAt must be in the future")
        Instant expiresAt) {
}
