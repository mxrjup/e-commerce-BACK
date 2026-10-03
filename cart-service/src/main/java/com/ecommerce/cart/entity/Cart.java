package com.ecommerce.cart.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "panier")
@Getter
@Setter
@NoArgsConstructor
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Authenticated user identifier.
     * Null when the cart belongs to an anonymous guest user.
     */
    @Column(name = "utilisateur_id")
    private Long userId;

    /**
     * Unique session token (UUID) identifying the cart in guest mode.
     */
    @Column(name = "session_token")
    private String sessionToken;

    /**
     * Timestamp of the last update made to the cart.
     */
    @Column(name = "date_maj", nullable = false)
    private Instant updatedAt;

    /**
     * List of line items associated with this cart.
     * - cascade = ALL: persisting or removing a cart propagates to its items.
     * - orphanRemoval = true: removing an item from this list deletes it from the database.
     */
    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CartItem> items = new ArrayList<>();

    public Cart(Long userId, String sessionToken) {
        this.userId = userId;
        this.sessionToken = sessionToken;
        this.updatedAt = Instant.now();
    }

    /**
     * Automatically update updatedAt timestamp before persisting or updating.
     */
    @PrePersist
    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * Helper method to add an item while synchronizing the bidirectional relation.
     */
    public void addItem(CartItem item) {
        this.items.add(item);
        item.setCart(this);
    }

    /**
     * Helper method to remove an item while synchronizing the bidirectional relation.
     */
    public void removeItem(CartItem item) {
        this.items.remove(item);
        item.setCart(null);
    }

    /**
     * Clear all items in this cart.
     */
    public void clearItems() {
        for (CartItem item : this.items) {
            item.setCart(null);
        }
        this.items.clear();
    }
}
