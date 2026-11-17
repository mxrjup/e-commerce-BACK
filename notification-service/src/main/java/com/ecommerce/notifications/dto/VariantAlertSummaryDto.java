package com.ecommerce.notifications.dto;

import com.ecommerce.notifications.entity.VariantAlertType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VariantAlertSummaryDto {
    private Long id;
    private Long variantId;
    private String sku;
    private String productName;
    private VariantAlertType alertType;
    private Integer currentStock;
    private Integer threshold;
    private boolean resolved;
}