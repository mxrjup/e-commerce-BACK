package com.ecommerce.cart.dto;

import com.ecommerce.cart.entity.Cart;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartResponse {

    private Long id;
    private Long userId;
    private String sessionToken;
    private Instant updatedAt;
    private List<CartItemResponse> items;
    private int totalItems;

    public static CartResponse fromEntity(Cart cart) {
        List<CartItemResponse> itemResponses = (cart.getItems() == null)
                ? Collections.emptyList()
                : cart.getItems().stream()
                .map(CartItemResponse::fromEntity)
                .collect(Collectors.toList());

        int count = itemResponses.stream()
                .mapToInt(CartItemResponse::getQuantity)
                .sum();

        return CartResponse.builder()
                .id(cart.getId())
                .userId(cart.getUserId())
                .sessionToken(cart.getSessionToken())
                .updatedAt(cart.getUpdatedAt())
                .items(itemResponses)
                .totalItems(count)
                .build();
    }
}
