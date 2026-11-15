package com.ecommerce.order.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "saved_payment_method")
@Getter
@Setter
@NoArgsConstructor
public class SavedPaymentMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "stripe_customer_id", nullable = false)
    private String stripeCustomerId;

    @Column(name = "stripe_payment_method_id", nullable = false, unique = true)
    private String stripePaymentMethodId;

    @Column(name = "brand")
    private String brand;

    @Column(name = "last4")
    private String last4;

    @Column(name = "is_default")
    private boolean defaultPaymentMethod;
}
