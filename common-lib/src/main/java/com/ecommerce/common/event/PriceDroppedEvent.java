package com.ecommerce.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Builder;

/**
 * The unit price of a variant went down. Prices are excluding taxes, like
 * everywhere else in the catalog.
 */
@Builder
public record PriceDroppedEvent(
        UUID eventId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant occurredAt,
        Long productId,
        Long variantId,
        BigDecimal previousUnitPriceExclTax,
        BigDecimal newUnitPriceExclTax
) implements CatalogEvent {

    public PriceDroppedEvent {
        Objects.requireNonNull(variantId, "variantId");
        Objects.requireNonNull(previousUnitPriceExclTax, "previousUnitPriceExclTax");
        Objects.requireNonNull(newUnitPriceExclTax, "newUnitPriceExclTax");
        eventId = eventId != null ? eventId : UUID.randomUUID();
        occurredAt = occurredAt != null ? occurredAt : Instant.now();
    }
}
