package com.ecommerce.cart.service;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedOrderEventRepository extends JpaRepository<ProcessedOrderEvent, Long> {
    boolean existsByOrderId(Long orderId);
}
