package com.ecommerce.catalog.dto;

import com.ecommerce.catalog.entity.Attribute;

import java.util.List;

/** An attribute and its values, in display order. */
public record AttributeResponse(Long id, String name, List<Value> values) {

    public record Value(Long id, String value, int position) {
    }

    public static AttributeResponse from(Attribute attribute) {
        return new AttributeResponse(attribute.getId(), attribute.getName(), attribute.getValues().stream()
                .map(value -> new Value(value.getId(), value.getValue(), value.getPosition()))
                .toList());
    }
}
