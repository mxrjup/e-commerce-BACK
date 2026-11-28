package com.ecommerce.mcp.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * @param protectedPaths servlet URL patterns of the MCP transport. The
 *                       defaults cover the endpoints of the Spring AI MCP
 *                       Server (M01 #50): SSE ({@code /sse},
 *                       {@code /mcp/message}) and Streamable HTTP
 *                       ({@code /mcp}). If M01 moves them, this list moves too.
 */
@ConfigurationProperties("mcp.security")
public record McpSecurityProperties(List<String> protectedPaths) {

    public McpSecurityProperties {
        protectedPaths = protectedPaths == null || protectedPaths.isEmpty()
                ? List.of("/sse", "/mcp", "/mcp/*")
                : List.copyOf(protectedPaths);
    }
}
