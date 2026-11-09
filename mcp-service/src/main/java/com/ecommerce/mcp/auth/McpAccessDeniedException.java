package com.ecommerce.mcp.auth;

import com.ecommerce.mcp.token.McpScope;

/** The token is valid but lacks the scope the tool needs. Mapped to HTTP 403. */
public class McpAccessDeniedException extends RuntimeException {

    public McpAccessDeniedException(McpScope missing) {
        super("This MCP token lacks the scope '" + missing.value() + "'");
    }

    public McpAccessDeniedException(String message) {
        super(message);
    }
}
