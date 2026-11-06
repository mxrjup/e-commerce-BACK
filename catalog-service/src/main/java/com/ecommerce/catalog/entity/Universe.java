package com.ecommerce.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A cross-category grouping shown on the storefront ("Gaming", "Télétravail").
 */
@Getter
@Setter
@Entity
@Table(name = "univers")
public class Universe extends AuditedEntity {

    @Column(name = "nom", nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 120)
    private String slug;

    @Column(length = 2000)
    private String description;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(nullable = false)
    private int position;
}
