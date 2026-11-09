package com.ecommerce.common.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Builder;

/**
 * Published when an order status changes during its delivery lifecycle.
 */
@Builder
public record OrderStatusChangedEvent(
        UUID eventId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant occurredAt,
        Long orderId,
        String orderNumber,
        String previousStatus,
        String newStatus
) implements DomainEvent {

    public OrderStatusChangedEvent {
        Objects.requireNonNull(orderNumber, "orderNumber");
        Objects.requireNonNull(previousStatus, "previousStatus");
        Objects.requireNonNull(newStatus, "newStatus");
        eventId = eventId != null ? eventId : UUID.randomUUID();
        occurredAt = occurredAt != null ? occurredAt : Instant.now();
    }
}
