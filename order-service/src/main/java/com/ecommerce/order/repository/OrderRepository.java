package com.ecommerce.order.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import com.ecommerce.order.entity.Order;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long>{

      /**
     * Find all order made by the authenticated user ID.
     * Spring Data automatically generates:
     * SELECT * FROM commande WHERE utilisateur_id = ?
     */
    List<Order> findByUserId(Long userId);


     /**
     * Find the order with the associated orderNumber.
     * Spring Data automatically generates:
     * SELECT * FROM commande WHERE numero = ?
     */
    Optional<Order> findByOrderNumber(String orderNumber);


    /**
     * Find the last order made for a year (ex : prefix "CMD-2026-")
     * In order to allow the order generator to increase the number
     * SELECT * 
        FROM commande 
        WHERE numero LIKE 'CMD-2026-%' 
        ORDER BY numero DESC 
        LIMIT 1;
     */
    Optional<Order> findFirstByOrderNumberStartingWithOrderByOrderNumberDesc(String prefix);


}
