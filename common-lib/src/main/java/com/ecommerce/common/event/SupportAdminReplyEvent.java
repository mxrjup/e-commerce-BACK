package com.ecommerce.common.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Builder;

/**
 * Published on {@code sav.reponse-admin} when a collaborator answers a
 * support ticket, so that its author gets notified. The reply itself is not
 * copied in the event: the notification links to the ticket.
 */
@Builder
public record SupportAdminReplyEvent(
        UUID eventId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant occurredAt,
        Long ticketId,
        Long userId,
        String ticketSubject
) implements DomainEvent {

    public SupportAdminReplyEvent {
        Objects.requireNonNull(ticketId, "ticketId");
        // Notifications cannot tell whom to notify without it.
        Objects.requireNonNull(userId, "userId");
        eventId = eventId != null ? eventId : UUID.randomUUID();
        occurredAt = occurredAt != null ? occurredAt : Instant.now();
    }
}
