package com.ecommerce.order.dto;

import com.ecommerce.order.entity.Order;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderDetailResponse(
    Long id,
    String orderNumber,
    String status,
    String deliveryMode,
    BigDecimal totalExclTax,
    BigDecimal totalInclTax,
    Instant createdAt,
    List<OrderItemResponse> items
) {
    public static OrderDetailResponse fromEntity(Order order) {
        return new OrderDetailResponse(
            order.getId(),
            order.getOrderNumber(),
            order.getStatus() != null ? order.getStatus().name() : null,
            order.getDeliveryMode() != null ? order.getDeliveryMode().name() : null,
            order.getTotalExclTax(),
            order.getTotalInclTax(),
            order.getCreatedAt(),
            order.getItems() == null ? List.of() : order.getItems().stream()
                .map(OrderItemResponse::fromEntity)
                .toList()
        );
    }

    public record OrderItemResponse(
        Long id,
        Long variantId,
        String designation,
        BigDecimal unitPriceExclTax,
        BigDecimal vatRate,
        int quantity,
        BigDecimal subTotalExclTax,
        BigDecimal subTotalInclTax
    ) {
        public static OrderItemResponse fromEntity(com.ecommerce.order.entity.OrderItem item) {
            return new OrderItemResponse(
                item.getId(),
                item.getVariantId(),
                item.getDesignation(),
                item.getUnitPriceExclTax(),
                item.getVatRate(),
                item.getQuantity(),
                item.getSubTotalExclTax(),
                item.getSubTotalInclTax()
            );
        }
    }
}
