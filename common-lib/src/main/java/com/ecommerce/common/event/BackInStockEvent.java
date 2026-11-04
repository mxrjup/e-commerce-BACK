package com.ecommerce.common.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import lombok.Builder;

/**
 * A variant that was out of stock can be ordered again.
 */
@Builder
public record BackInStockEvent(
        UUID eventId,
        Instant occurredAt,
        Long productId,
        Long variantId,
        int availableQuantity
) implements CatalogEvent {

    public BackInStockEvent {
        Objects.requireNonNull(variantId, "variantId");
        eventId = eventId != null ? eventId : UUID.randomUUID();
        occurredAt = occurredAt != null ? occurredAt : Instant.now();
    }
}
