package com.ecommerce.cart.dto;

import com.ecommerce.cart.entity.CartItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemResponse {

    private Long id;
    private Long variantId;
    private int quantity;

    public static CartItemResponse fromEntity(CartItem item) {
        return CartItemResponse.builder()
                .id(item.getId())
                .variantId(item.getVariantId())
                .quantity(item.getQuantity())
                .build();
    }
}
