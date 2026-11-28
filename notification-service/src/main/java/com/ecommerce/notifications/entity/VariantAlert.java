package com.ecommerce.notifications.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
    name = "alerte_variante",
    indexes = {
        @Index(name = "idx_alerte_variante_variant_id", columnList = "variant_id"),
        @Index(name = "idx_alerte_variante_resolved", columnList = "resolved, created_at DESC")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VariantAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Column(name = "sku", nullable = false, length = 64)
    private String sku;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 32)
    private VariantAlertType alertType;

    @Column(name = "current_stock", nullable = false)
    private Integer currentStock;

    @Column(name = "threshold", nullable = false)
    private Integer threshold;

    @Builder.Default
    @Column(name = "resolved", nullable = false)
    private boolean resolved = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}