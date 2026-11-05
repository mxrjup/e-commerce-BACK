package com.ecommerce.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import lombok.Builder;

/**
 * Published on {@code order.paid} once Stripe has confirmed the payment of an
 * order. Consumed by the cart (to empty it) and by the notifications.
 *
 * @param userId       owner of the order; checkout requires an account
 * @param sessionToken guest cart token to empty as well, {@code null} when unknown
 * @param totalInclTax amount actually paid, taxes and shipping included
 */
@Builder
public record OrderPaidEvent(
        UUID eventId,
        Instant occurredAt,
        Long orderId,
        String orderNumber,
        Long userId,
        String sessionToken,
        BigDecimal totalInclTax
) {

    public OrderPaidEvent {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(orderNumber, "orderNumber");
        eventId = eventId != null ? eventId : UUID.randomUUID();
        occurredAt = occurredAt != null ? occurredAt : Instant.now();
    }
}
