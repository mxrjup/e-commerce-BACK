package com.ecommerce.common.event;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Common shape of every event published on Kafka.
 *
 * <p>{@code eventId} is unique per event, not per business object: a consumer
 * whose effect is not naturally idempotent (sending an e-mail, for instance)
 * stores the ids it has processed and skips a redelivered one.
 *
 * <p>It only protects against Kafka delivering the same record twice. Two
 * publications of the same business fact (two Stripe webhooks for one
 * payment, processed concurrently) carry two different {@code eventId}s:
 * against that, deduplicate on a business key, such as {@code orderId}.
 */
public interface DomainEvent {

    UUID eventId();

    /** Written as ISO-8601 ({@code 2026-11-03T08:00:00Z}), readable in Kafka UI. */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Instant occurredAt();
}
