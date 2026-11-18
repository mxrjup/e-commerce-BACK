package com.ecommerce.notifications.dto;

import com.ecommerce.notifications.entity.CatalogAlertType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCatalogAlertRequestDto {

    private Long productId;

    @NotNull(message = "variantId is required")
    private Long variantId;

    @NotNull(message = "alertType is required")
    private CatalogAlertType alertType;

    @DecimalMin(value = "0.01", message = "targetPriceExclTax must be greater than 0")
    private BigDecimal targetPriceExclTax;
}