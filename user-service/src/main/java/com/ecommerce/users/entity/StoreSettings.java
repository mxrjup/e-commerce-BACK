package com.ecommerce.users.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "store_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoreSettings {

    public static final Long DEFAULT_ID = 1L;

    @Id
    private Long id;

    // Coordonnées de contact
    @Column(name = "store_name", nullable = false)
    private String storeName;

    @Column(name = "contact_email", nullable = false)
    private String contactEmail;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "support_hours")
    private String supportHours;

    @Column(name = "postal_address")
    private String postalAddress;

    // Informations légales
    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "siret", length = 32)
    private String siret;

    @Column(name = "vat_number", length = 32)
    private String vatNumber;

    @Column(name = "legal_notice", columnDefinition = "TEXT")
    private String legalNotice;

    @Column(name = "terms_url")
    private String termsUrl;

    @Column(name = "privacy_policy_url")
    private String privacyPolicyUrl;

    // Frais de port et seuils
    @Column(name = "standard_shipping_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal standardShippingFee;

    @Column(name = "express_shipping_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal expressShippingFee;

    @Column(name = "free_shipping_threshold", nullable = false, precision = 10, scale = 2)
    private BigDecimal freeShippingThreshold;

    @Column(name = "minimum_order_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal minimumOrderAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void onSave() {
        this.updatedAt = Instant.now();
    }
}   