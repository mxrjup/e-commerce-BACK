package com.ecommerce.notifications.dto;

import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCatalogAlertRequestDto {

    @DecimalMin(value = "0.01", message = "targetPriceExclTax must be greater than 0")
    private BigDecimal targetPriceExclTax;

    private Boolean active;
}