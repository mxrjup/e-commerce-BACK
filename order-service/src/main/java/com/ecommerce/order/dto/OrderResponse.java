package com.ecommerce.order.dto;

import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderItem;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Builder
public class OrderResponse {

    private Long id;
    private String orderNumber;
    private Long userId;
    private String status;
    private String deliveryMode;
    private Long shippingAddressId;
    private BigDecimal totalExclTax;
    private BigDecimal totalInclTax;
    private Instant createdAt;
    private List<OrderItemResponse> items;

    public static OrderResponse fromEntity(Order order) {
        return OrderResponse.builder()
            .id(order.getId())
            .orderNumber(order.getOrderNumber())
            .userId(order.getUserId())
            .status(order.getStatus() != null ? order.getStatus().name() : null)
            .deliveryMode(order.getDeliveryMode() != null ? order.getDeliveryMode().name() : null)
            .shippingAddressId(order.getShippingAddressId())
            .totalExclTax(order.getTotalExclTax())
            .totalInclTax(order.getTotalInclTax())
            .createdAt(order.getCreatedAt())
            .items(order.getItems() == null ? List.of() : order.getItems().stream()
                .map(OrderItemResponse::fromEntity)
                .toList())
            .build();
    }

    @Getter
    @Builder
    public static class OrderItemResponse {
        private Long id;
        private Long variantId;
        private String designation;
        private BigDecimal unitPriceExclTax;
        private BigDecimal vatRate;
        private int quantity;
        private BigDecimal subTotalExclTax;
        private BigDecimal subTotalInclTax;

        public static OrderItemResponse fromEntity(OrderItem item) {
            return OrderItemResponse.builder()
                .id(item.getId())
                .variantId(item.getVariantId())
                .designation(item.getDesignation())
                .unitPriceExclTax(item.getUnitPriceExclTax())
                .vatRate(item.getVatRate())
                .quantity(item.getQuantity())
                .subTotalExclTax(item.getSubTotalExclTax())
                .subTotalInclTax(item.getSubTotalInclTax())
                .build();
        }
    }
}
