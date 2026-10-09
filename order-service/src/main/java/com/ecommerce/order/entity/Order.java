package com.ecommerce.order.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "commande")
@Getter
@Setter
@NoArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero", nullable = false, unique = true)
    private String orderNumber;

    @Column(name = "utilisateur_id", nullable = false)
    private Long userId;

    @Column(name = "adresse_livraison_id")
    private Long shippingAddressId;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false)
    private OrderStatus status = OrderStatus.NEW;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode_livraison")
    private DeliveryMode deliveryMode;

    @Column(name = "total_ht", precision = 10, scale = 2)
    private BigDecimal totalExclTax = BigDecimal.ZERO;

    @Column(name = "total_ttc", precision = 10, scale = 2)
    private BigDecimal totalInclTax = BigDecimal.ZERO;

    @Column(name = "stripe_session_id")
    private String stripeSessionId;

    @Column(name = "transporteur")
    private String carrier;

    @Column(name = "numero_suivi")
    private String trackingNumber;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    public void removeItem(OrderItem item) {
        items.remove(item);
        item.setOrder(null);
    }
}
