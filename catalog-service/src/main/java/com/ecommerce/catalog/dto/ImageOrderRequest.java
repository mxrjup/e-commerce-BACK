package com.ecommerce.catalog.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * The new order of the images of a variant (C06): every image id, once, the
 * first becoming the main thumbnail.
 */
public record ImageOrderRequest(
        @NotNull(message = "imageIds is required")
        @Size(max = 50, message = "imageIds must list at most 50 images")
        List<@NotNull(message = "imageIds must not contain null") Long> imageIds) {
}
