package com.ecommerce.catalog.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The generic product sheet: what every variant has in common. What is
 * actually sold, priced and stocked is a {@link Variant}.
 */
@Getter
@Setter
@Entity
@Table(name = "produit")
public class Product extends AuditedEntity {

    @Column(name = "titre", nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 120)
    private String slug;

    /** HTML from the back-office editor. */
    @Column(length = 20000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private ProductStatus status = ProductStatus.BROUILLON;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "marque_id", nullable = false)
    private Brand brand;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categorie_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tva_id", nullable = false)
    private VatRate vatRate;

    /** Loaded for a whole page of products at once when a list needs them. */
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    @BatchSize(size = 50)
    private List<Variant> variants = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position, id")
    private List<ProductSpecification> specifications = new ArrayList<>();

    @ManyToMany
    @JoinTable(name = "produit_univers",
            joinColumns = @JoinColumn(name = "produit_id"),
            inverseJoinColumns = @JoinColumn(name = "univers_id"))
    private Set<Universe> universes = new LinkedHashSet<>();

    /** Adds a variant and sets its back-reference, so both sides agree. */
    public Variant addVariant(Variant variant) {
        variant.setProduct(this);
        variants.add(variant);
        return variant;
    }

    public ProductSpecification addSpecification(String key, String value) {
        ProductSpecification specification = new ProductSpecification();
        specification.setProduct(this);
        specification.setKey(key);
        specification.setValue(value);
        specification.setPosition(specifications.size());
        specifications.add(specification);
        return specification;
    }
}
