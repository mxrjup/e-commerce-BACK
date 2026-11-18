package com.ecommerce.cart.dto;

import com.ecommerce.cart.client.CatalogVariantResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuggestedAccessoryResponse {

    private Long variantId;
    private String productName;
    private BigDecimal unitPriceExclTax;
    private BigDecimal vatRate;
    private Boolean available;
    private String imageUrl;

    public static SuggestedAccessoryResponse fromCatalog(CatalogVariantResponse variant) {
        return SuggestedAccessoryResponse.builder()
                .variantId(variant.id())
                .productName(variant.designation())
                .unitPriceExclTax(variant.unitPriceExclTax())
                .vatRate(variant.vatRate())
                .available(variant.available())
                .imageUrl(variant.imageUrl())
                .build();
    }
}
