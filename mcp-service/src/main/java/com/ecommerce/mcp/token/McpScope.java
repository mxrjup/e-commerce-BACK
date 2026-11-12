package com.ecommerce.mcp.token;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * What an MCP token may read. One scope per service the tools of M02 (#51)
 * query, read-only: no MCP tool writes anything.
 */
public enum McpScope {

    CATALOG_READ("catalog:read"),
    ORDERS_READ("orders:read"),
    USERS_READ("users:read");

    private final String value;

    McpScope(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    /** Lenient on case only: an unknown scope is rejected, never ignored. */
    @JsonCreator
    public static McpScope fromValue(String value) {
        return Arrays.stream(values())
                .filter(scope -> scope.value.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown MCP scope '" + value + "'"));
    }
}
