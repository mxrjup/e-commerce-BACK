package com.ecommerce.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * A VAT rate, as a percentage: {@code 20.00} means 20 %.
 */
@Getter
@Setter
@Entity
@Table(name = "vat_rate")
public class VatRate extends AuditedEntity {

    @Column(nullable = false, length = 50)
    private String label;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal rate;

    @Column(name = "is_default", nullable = false)
    private boolean defaultRate;
}
