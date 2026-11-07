package com.ecommerce.order.service;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProcessedOrderStatusEventRepository extends JpaRepository<ProcessedOrderStatusEvent, Long> {
    boolean existsByEventId(UUID eventId);
}
