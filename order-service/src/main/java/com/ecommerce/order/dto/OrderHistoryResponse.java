package com.ecommerce.order.dto;

import com.ecommerce.order.entity.Order;
import java.math.BigDecimal;
import java.time.Instant;

public record OrderHistoryResponse(
    Long id,
    String orderNumber,
    String status,
    String deliveryMode,
    BigDecimal totalInclTax,
    int itemCount,
    Instant createdAt
) {
    public static OrderHistoryResponse fromEntity(Order order) {
        return new OrderHistoryResponse(
            order.getId(),
            order.getOrderNumber(),
            order.getStatus() != null ? order.getStatus().name() : null,
            order.getDeliveryMode() != null ? order.getDeliveryMode().name() : null,
            order.getTotalInclTax(),
            order.getItems() != null ? order.getItems().size() : 0,
            order.getCreatedAt()
        );
    }
}
