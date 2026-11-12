package com.ecommerce.mcp.auth;

import com.ecommerce.mcp.token.McpScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Stands for the endpoints of the Spring AI MCP Server, which M01 (#50) adds:
 * same paths, and a « tool » that checks its scope the way M02 tools will.
 */
@RestController
class FakeMcpTransport {

    @GetMapping("/sse")
    Map<String, Object> sse() {
        return Map.of("connected", true);
    }

    @PostMapping("/mcp/message")
    Map<String, Object> catalogTool() {
        McpPrincipal principal = McpAuthentication.requireScope(McpScope.CATALOG_READ);
        return Map.of("token", principal.tokenName());
    }

    @PostMapping("/mcp")
    Map<String, Object> ordersTool() {
        McpPrincipal principal = McpAuthentication.requireScope(McpScope.ORDERS_READ);
        return Map.of("token", principal.tokenName());
    }
}
