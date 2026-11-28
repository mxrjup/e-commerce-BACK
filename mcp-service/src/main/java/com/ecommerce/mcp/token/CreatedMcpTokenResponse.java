package com.ecommerce.mcp.token;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * The answer to a creation, and the only time the token ever leaves the
 * service: it cannot be read again, only revoked and replaced.
 */
public record CreatedMcpTokenResponse(@JsonUnwrapped McpTokenResponse metadata, String token) {
}
