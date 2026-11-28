package com.ecommerce.mcp.auth;

import com.ecommerce.mcp.token.McpScope;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Optional;

/**
 * What a tool (M02 #51) asks before reading anything:
 *
 * <pre>{@code
 * McpAuthentication.requireScope(McpScope.CATALOG_READ);
 * }</pre>
 *
 * <p>The principal lives in the attributes of the HTTP request that carried
 * the tool call, set there by {@link McpAuthenticationFilter}. It is
 * therefore only visible on the thread handling that request.
 */
public final class McpAuthentication {

    static final String ATTRIBUTE = McpPrincipal.class.getName();

    private McpAuthentication() {
    }

    public static Optional<McpPrincipal> current() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return Optional.empty();
        }
        return Optional.ofNullable((McpPrincipal) attributes.getAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST));
    }

    public static McpPrincipal requireScope(McpScope scope) {
        McpPrincipal principal = current()
                .orElseThrow(() -> new McpAccessDeniedException("No MCP token on this request"));
        if (!principal.has(scope)) {
            throw new McpAccessDeniedException(scope);
        }
        return principal;
    }
}
