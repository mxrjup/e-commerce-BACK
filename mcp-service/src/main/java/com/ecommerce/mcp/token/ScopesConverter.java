package com.ecommerce.mcp.token;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Stores the scopes space-separated, in a stable order. */
@Converter
public class ScopesConverter implements AttributeConverter<Set<McpScope>, String> {

    @Override
    public String convertToDatabaseColumn(Set<McpScope> scopes) {
        return EnumSet.copyOf(scopes).stream().map(McpScope::value).collect(Collectors.joining(" "));
    }

    @Override
    public Set<McpScope> convertToEntityAttribute(String column) {
        Set<McpScope> scopes = EnumSet.noneOf(McpScope.class);
        Arrays.stream(column.trim().split("\\s+")).map(McpScope::fromValue).forEach(scopes::add);
        return scopes;
    }
}
