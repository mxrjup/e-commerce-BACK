package com.ecommerce.mcp.token;

import java.time.Instant;
import java.util.Set;

/** A token as the back-office lists it: everything but the token itself. */
public record McpTokenResponse(
        Long id,
        String name,
        String prefix,
        Set<McpScope> scopes,
        McpTokenStatus status,
        String createdBy,
        Instant createdAt,
        Instant expiresAt,
        Instant lastUsedAt,
        String revokedBy,
        Instant revokedAt) {

    static McpTokenResponse from(McpToken token, Instant now) {
        return new McpTokenResponse(token.getId(), token.getName(), token.getPrefix(), token.getScopes(),
                token.statusAt(now), token.getCreatedBy(), token.getCreatedAt(), token.getExpiresAt(),
                token.getLastUsedAt(), token.getRevokedBy(), token.getRevokedAt());
    }
}
