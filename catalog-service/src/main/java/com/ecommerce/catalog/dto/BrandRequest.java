package com.ecommerce.catalog.dto;

import com.ecommerce.catalog.support.Slugs;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Creates or replaces a brand. Without a slug, one is derived from the name.
 */
public record BrandRequest(
        @NotBlank(message = "name must not be blank")
        @Size(max = 100, message = "name must be at most 100 characters")
        String name,

        @Size(max = 120, message = "slug must be at most 120 characters")
        @Pattern(regexp = Slugs.PATTERN, message = "slug must be lowercase words separated by hyphens")
        String slug,

        @Size(max = 2000, message = "description must be at most 2000 characters")
        String description,

        @Size(max = 500, message = "logoUrl must be at most 500 characters")
        String logoUrl) {
}
