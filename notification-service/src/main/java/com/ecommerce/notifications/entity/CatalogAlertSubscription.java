package com.ecommerce.notifications.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
    name = "catalog_alert_subscription",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_catalog_alert_recipient_variant_type",
            columnNames = {"recipient_id", "variant_id", "alert_type"}
        )
    },
    indexes = {
        @Index(name = "idx_catalog_alert_variant_type_active", columnList = "variant_id, alert_type, active"),
        @Index(name = "idx_catalog_alert_recipient_created", columnList = "recipient_id, created_at DESC")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CatalogAlertSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_id", nullable = false, length = 128)
    private String recipientId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 32)
    private CatalogAlertType alertType;

    /**
     * Seuil de prix HT optionnel pour PRICE_DROP : si renseigne, l'alerte ne se declenche
     * que lorsque newUnitPriceExclTax <= targetPriceExclTax. Si null, toute baisse declenche.
     */
    @Column(name = "target_price_excl_tax", precision = 12, scale = 2)
    private BigDecimal targetPriceExclTax;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "triggered_at")
    private Instant triggeredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public void markTriggered() {
        this.active = false;
        this.triggeredAt = Instant.now();
    }
}