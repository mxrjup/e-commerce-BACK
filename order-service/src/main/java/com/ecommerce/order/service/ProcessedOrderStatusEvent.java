package com.ecommerce.order.service;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "processed_order_status_events",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_processed_order_status_event_event_id",
        columnNames = "event_id"
    )
)
@Getter
@Setter
@NoArgsConstructor
public class ProcessedOrderStatusEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public ProcessedOrderStatusEvent(UUID eventId, Long orderId) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.processedAt = Instant.now();
    }
}
