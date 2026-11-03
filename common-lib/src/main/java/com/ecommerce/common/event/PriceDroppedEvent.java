package com.ecommerce.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import lombok.Builder;

/**
 * The unit price of a variant went down. Prices are excluding taxes, like
 * everywhere else in the catalog.
 */
@Builder
public record PriceDroppedEvent(
        UUID eventId,
        Instant occurredAt,
        Long productId,
        Long variantId,
        BigDecimal previousUnitPriceExclTax,
        BigDecimal newUnitPriceExclTax
) implements CatalogEvent {

    public PriceDroppedEvent {
        Objects.requireNonNull(variantId, "variantId");
        eventId = eventId != null ? eventId : UUID.randomUUID();
        occurredAt = occurredAt != null ? occurredAt : Instant.now();
    }
}
