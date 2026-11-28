package com.ecommerce.order.service;

import com.ecommerce.common.event.OrderStatusChangedEvent;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderStatusChangedListener {

    private final OrderRepository orderRepository;
    private final ProcessedOrderStatusEventRepository processedOrderStatusEventRepository;

    @KafkaListener(
        topics = "${app.kafka.topics.order-status-changed:order-status-changed}",
        groupId = "order-service"
    )
    @Transactional
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        if (event == null || event.orderId() == null) {
            log.warn("Received invalid OrderStatusChangedEvent: {}", event);
            return;
        }

        if (processedOrderStatusEventRepository.existsByEventId(event.eventId())) {
            log.info("Ignoring duplicate status event {} for order {}", event.eventId(), event.orderId());
            return;
        }

        Optional<Order> orderOpt = orderRepository.findById(event.orderId());
        if (orderOpt.isEmpty()) {
            log.warn(
                "Order {} not found for status update {} -> {}",
                event.orderId(),
                event.previousStatus(),
                event.newStatus()
            );
            return;
        }

        Order order = orderOpt.get();
        log.info(
            "Order status changed for {}: {} -> {}",
            order.getOrderNumber(),
            event.previousStatus(),
            event.newStatus()
        );

        switch (event.newStatus()) {
            case "IN_PREPARATION" -> order.setStatus(com.ecommerce.order.entity.OrderStatus.IN_PREPARATION);
            case "SHIPPED" -> order.setStatus(com.ecommerce.order.entity.OrderStatus.SHIPPED);
            case "DELIVERED" -> order.setStatus(com.ecommerce.order.entity.OrderStatus.DELIVERED);
            case "CANCELLED" -> order.setStatus(com.ecommerce.order.entity.OrderStatus.CANCELLED);
            default -> log.info(
                "No business action defined for status {} on order {}",
                event.newStatus(),
                order.getOrderNumber()
            );
        }

        orderRepository.save(order);
        processedOrderStatusEventRepository.save(new ProcessedOrderStatusEvent(event.eventId(), event.orderId()));
    }
}
