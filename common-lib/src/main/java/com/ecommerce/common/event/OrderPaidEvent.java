package com.ecommerce.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Builder;

/**
 * Published on {@code order.paid} once Stripe has confirmed the payment of an
 * order. Consumed by the cart (to empty it), by the catalogue (to take the
 * paid quantities out of the stock, C21) and by the notifications.
 *
 * @param userId       owner of the order; checkout requires an account
 * @param sessionToken guest cart token to empty as well, {@code null} when unknown
 * @param totalInclTax amount actually paid, taxes and shipping included
 * @param items        what was paid, one entry per order line; never null,
 *                     empty when sent by a producer that predates this field
 */
@Builder
public record OrderPaidEvent(
        UUID eventId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant occurredAt,
        Long orderId,
        String orderNumber,
        Long userId,
        String sessionToken,
        BigDecimal totalInclTax,
        List<Item> items
) implements DomainEvent {

    /** One order line: the variant sold and how many units. */
    public record Item(Long variantId, int quantity) {
    }

    public OrderPaidEvent {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(orderNumber, "orderNumber");
        eventId = eventId != null ? eventId : UUID.randomUUID();
        occurredAt = occurredAt != null ? occurredAt : Instant.now();
        items = items != null ? List.copyOf(items) : List.of();
    }
}
