package com.ecommerce.cart.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
    name = "processed_order_events",
    uniqueConstraints = @UniqueConstraint(name = "uk_processed_order_event_order_id", columnNames = "order_id")
)
@Getter
@Setter
@NoArgsConstructor
public class ProcessedOrderEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public ProcessedOrderEvent(Long orderId) {
        this.orderId = orderId;
        this.processedAt = Instant.now();
    }
}
