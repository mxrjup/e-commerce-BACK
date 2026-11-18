package com.ecommerce.notifications.dto;

import com.ecommerce.notifications.entity.CatalogAlertType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
public class CatalogAlertResponseDto {
    private Long id;
    private Long productId;
    private Long variantId;
    private CatalogAlertType alertType;
    private BigDecimal targetPriceExclTax;
    private boolean active;
    private Instant triggeredAt;
    private Instant createdAt;
}