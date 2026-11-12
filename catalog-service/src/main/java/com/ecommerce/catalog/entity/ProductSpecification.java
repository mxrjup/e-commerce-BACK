package com.ecommerce.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A descriptive characteristic shared by every variant of a product:
 * "Garantie" = "2 ans", "Connectique" = "USB-C, Bluetooth".
 */
@Getter
@Setter
@Entity
@Table(name = "produit_specification")
public class ProductSpecification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produit_id", nullable = false)
    private Product product;

    @Column(name = "cle", nullable = false, length = 100)
    private String key;

    @Column(name = "valeur", nullable = false, length = 500)
    private String value;

    @Column(nullable = false)
    private int position;
}
