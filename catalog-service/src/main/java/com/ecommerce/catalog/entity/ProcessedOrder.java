package com.ecommerce.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A paid order whose quantities are already out of the stock (C21). The order
 * id is the key: a second delivery of the same order finds it and changes
 * nothing. A logical reference to order-service, without a foreign key.
 */
@Getter
@Entity
@Table(name = "commande_traitee")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedOrder {

    @Id
    @Column(name = "commande_id")
    private Long orderId;

    @Column(name = "numero_commande", nullable = false, length = 50)
    private String orderNumber;

    @Column(name = "date_traitement", nullable = false)
    private Instant processedAt;

    public ProcessedOrder(Long orderId, String orderNumber, Instant processedAt) {
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.processedAt = processedAt;
    }
}
