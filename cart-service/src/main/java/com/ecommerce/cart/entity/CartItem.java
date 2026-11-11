package com.ecommerce.cart.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ligne_panier")
@Getter
@Setter
@NoArgsConstructor
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Parent cart to which this line item belongs.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "panier_id", nullable = false)
    private Cart cart;

    /**
     * Product variant ID referencing the Catalog service.
     */
    @Column(name = "variante_id", nullable = false)
    private Long variantId;

    /**
     * Quantity of this variant added to the cart (must be >= 1).
     */
    @Column(name = "quantite", nullable = false)
    private int quantity;

    public CartItem(Cart cart, Long variantId, int quantity) {
        this.cart = cart;
        this.variantId = variantId;
        this.quantity = quantity;
    }
}
