package com.ecommerce.cart.repository;

import com.ecommerce.cart.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    /**
     * Find a cart item by its ID and its parent cart ID.
     * Ensures an operation on an item belongs to the caller's cart.
     */
    Optional<CartItem> findByIdAndCartId(Long id, Long cartId);

    /**
     * Find an item in a cart by the variant ID.
     */
    Optional<CartItem> findByCartIdAndVariantId(Long cartId, Long variantId);
}
