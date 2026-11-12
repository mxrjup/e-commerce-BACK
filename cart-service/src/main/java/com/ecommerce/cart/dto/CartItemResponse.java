package com.ecommerce.cart.dto;

import com.ecommerce.cart.entity.CartItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemResponse {

    private Long id;
    private Long variantId;
    private int quantity;
    private String productName;
    private BigDecimal unitPriceExclTax;
    private BigDecimal vatRate;
    private Boolean available;
    private String imageUrl;
    private String status;

    public static CartItemResponse fromEntity(CartItem item) {
        return CartItemResponse.builder()
                .id(item.getId())
                .variantId(item.getVariantId())
                .quantity(item.getQuantity())
                .productName(null)
                .unitPriceExclTax(null)
                .vatRate(null)
                .available(false)
                .imageUrl(null)
                .status("UNAVAILABLE")
                .build();
    }
}
