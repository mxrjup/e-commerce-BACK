package com.ecommerce.cart.repository;

import com.ecommerce.cart.entity.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartRepository extends JpaRepository<Cart, Long> {
    /**
     * Find a cart by the authenticated user ID.
     * Spring Data automatically generates:
     * SELECT * FROM panier WHERE utilisateur_id = ?
     */
    Optional<Cart> findByUserId(Long userId);

    /**
     * Find a cart by the guest session token.
     * Spring Data automatically generates:
     * SELECT * FROM panier WHERE session_token = ?
     */
    Optional<Cart> findBySessionToken(String sessionToken);
    
    /**
     * Optimized query: fetches the cart AND all its items in a single SQL query
     * using LEFT JOIN FETCH, avoiding the N+1 select problem.
     */
    @Query("SELECT c FROM Cart c LEFT JOIN FETCH c.items WHERE c.userId = :userId")
    Optional<Cart> findByUserIdWithItems(@Param("userId") Long userId);
    /**
     * Optimized query for guest cart with items fetched eagerly.
     */
    @Query("SELECT c FROM Cart c LEFT JOIN FETCH c.items WHERE c.sessionToken = :sessionToken")
    Optional<Cart> findBySessionTokenWithItems(@Param("sessionToken") String sessionToken);
}