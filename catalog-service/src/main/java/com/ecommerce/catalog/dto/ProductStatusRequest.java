package com.ecommerce.catalog.dto;

import com.ecommerce.catalog.entity.ProductStatus;
import jakarta.validation.constraints.NotNull;

/** Publishes, archives or republishes a product (C04). */
public record ProductStatusRequest(
        @NotNull(message = "status is required")
        ProductStatus status) {
}
