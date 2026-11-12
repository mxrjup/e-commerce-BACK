package com.ecommerce.catalog.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The unit actually sold: one combination of attribute values of a product,
 * with its own SKU, prices, weight and stock. The cart and the orders point
 * to a variant, never to a product.
 *
 * <p>Prices are excluding VAT; the rate comes from the product.
 */
@Getter
@Setter
@Entity
@Table(name = "variante")
public class Variant extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produit_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 64)
    private String sku;

    /** What the shop pays the supplier: back-office and margins only. */
    @Column(name = "prix_achat_ht", nullable = false, precision = 10, scale = 2)
    private BigDecimal purchasePriceExclTax;

    @Column(name = "prix_vente_ht", nullable = false, precision = 10, scale = 2)
    private BigDecimal salePriceExclTax;

    /** Null when the variant is not on sale. */
    @Column(name = "prix_remise_ht", precision = 10, scale = 2)
    private BigDecimal discountPriceExclTax;

    /** Fixed WEEE tax added to the price; null when not subject to it. */
    @Column(name = "ecotaxe", precision = 10, scale = 2)
    private BigDecimal ecoTax;

    /** In kilograms, for the shipping costs. */
    @Column(name = "poids", nullable = false, precision = 8, scale = 3)
    private BigDecimal weightKg;

    @Column(nullable = false)
    private int stock;

    /** At or below this stock level, the back-office flags the variant. */
    @Column(name = "seuil_alerte", nullable = false)
    private int alertThreshold;

    @ManyToMany
    @JoinTable(name = "variante_valeur",
            joinColumns = @JoinColumn(name = "variante_id"),
            inverseJoinColumns = @JoinColumn(name = "valeur_id"))
    private Set<AttributeValue> values = new LinkedHashSet<>();

    @OneToMany(mappedBy = "variant", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position, id")
    private List<VariantImage> images = new ArrayList<>();

    /** The price the customer pays before VAT: the discount when there is one. */
    public BigDecimal currentPriceExclTax() {
        return discountPriceExclTax != null ? discountPriceExclTax : salePriceExclTax;
    }

    public boolean isLowOnStock() {
        return stock <= alertThreshold;
    }

    public VariantImage addImage(String url) {
        VariantImage image = new VariantImage();
        image.setVariant(this);
        image.setUrl(url);
        image.setPosition(images.size() + 1);
        images.add(image);
        return image;
    }
}
