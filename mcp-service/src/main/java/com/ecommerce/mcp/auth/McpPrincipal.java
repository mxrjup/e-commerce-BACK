package com.ecommerce.mcp.auth;

import com.ecommerce.mcp.token.McpScope;

import java.util.Set;

/** The token behind the current MCP request. */
public record McpPrincipal(Long tokenId, String tokenName, Set<McpScope> scopes) {

    public boolean has(McpScope scope) {
        return scopes.contains(scope);
    }
}
