package com.ecommerce.catalog.dto;

import com.ecommerce.catalog.support.Slugs;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Creates or replaces a product sheet (C04, form C16). The status has its
 * own endpoint, and the variants theirs.
 *
 * @param slug           without one, derived from the title at creation, then kept as is
 * @param description    HTML from the back-office editor
 * @param vatRateId      without one, the default rate at creation, then the current one
 * @param universeIds    null or empty for none
 * @param specifications the whole list, in display order: it replaces the previous one
 */
public record AdminProductRequest(
        @NotBlank(message = "title must not be blank")
        @Size(max = 150, message = "title must be at most 150 characters")
        String title,

        @Size(max = 120, message = "slug must be at most 120 characters")
        @Pattern(regexp = Slugs.PATTERN, message = "slug must be lowercase words separated by hyphens")
        String slug,

        @Size(max = 20000, message = "description must be at most 20000 characters")
        String description,

        @NotNull(message = "brandId is required")
        Long brandId,

        @NotNull(message = "categoryId is required")
        Long categoryId,

        Long vatRateId,

        @Size(max = 20, message = "a product belongs to at most 20 universes")
        List<@NotNull(message = "universeIds must not contain null") Long> universeIds,

        @Size(max = 50, message = "a product has at most 50 specifications")
        List<@NotNull(message = "specifications must not contain null") @Valid Specification> specifications) {

    public record Specification(
            @NotBlank(message = "key must not be blank")
            @Size(max = 100, message = "key must be at most 100 characters")
            String key,

            @NotBlank(message = "value must not be blank")
            @Size(max = 500, message = "value must be at most 500 characters")
            String value) {
    }
}
