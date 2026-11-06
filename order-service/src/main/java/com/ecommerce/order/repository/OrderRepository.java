package com.ecommerce.order.repository;

import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * Find all orders made by the authenticated user ID.
     */
    List<Order> findByUserId(Long userId);

    /**
     * Find the order with the associated order number.
     */
    Optional<Order> findByOrderNumber(String orderNumber);

    /**
     * Find the order by its Stripe checkout session ID.
     */
    Optional<Order> findByStripeSessionId(String stripeSessionId);

    /**
     * Find the last order made for a given year prefix.
     */
    Optional<Order> findFirstByOrderNumberStartingWithOrderByOrderNumberDesc(String prefix);

    List<Order> findByStatus(OrderStatus status);

    List<Order> findByStatusAndCreatedAtBetween(OrderStatus status, Instant from, Instant to);

    List<Order> findByCreatedAtBetween(Instant from, Instant to);

    List<Order> findByUserIdAndCreatedAtBetween(Long userId, Instant from, Instant to);

    List<Order> findByUserIdAndStatus(Long userId, OrderStatus status);

    List<Order> findByUserIdAndStatusAndCreatedAtBetween(Long userId, OrderStatus status, Instant from, Instant to);
}
