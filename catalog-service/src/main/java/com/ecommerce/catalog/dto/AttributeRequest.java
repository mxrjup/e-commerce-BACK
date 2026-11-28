package com.ecommerce.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Creates or replaces an attribute and its values (C04, screen C17).
 *
 * @param values the whole list, in display order. A value sent with its
 *               {@code id} is kept, and renamed if its text changed; one
 *               without is created; an existing value left out is deleted,
 *               unless a variant uses it.
 */
public record AttributeRequest(
        @NotBlank(message = "name must not be blank")
        @Size(max = 50, message = "name must be at most 50 characters")
        String name,

        @NotNull(message = "values is required")
        @Size(max = 100, message = "an attribute has at most 100 values")
        List<@NotNull(message = "values must not contain null") @Valid Value> values) {

    public record Value(
            Long id,

            @NotBlank(message = "value must not be blank")
            @Size(max = 50, message = "value must be at most 50 characters")
            String value) {
    }
}
