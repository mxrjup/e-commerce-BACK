package com.ecommerce.cart.service;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "processed_order_events", uniqueConstraints = {
    @UniqueConstraint(name = "uk_processed_order_event_order_id", columnNames = "order_id")
})
@Getter
@Setter
@NoArgsConstructor
public class ProcessedOrderEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    public ProcessedOrderEvent(Long orderId) {
        this.orderId = orderId;
    }
}
