package com.ecommerce.mcp.token;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum McpTokenStatus {

    ACTIVE, EXPIRED, REVOKED;

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
