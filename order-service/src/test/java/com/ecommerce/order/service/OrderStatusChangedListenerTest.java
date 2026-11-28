package com.ecommerce.order.service;

import com.ecommerce.common.event.OrderStatusChangedEvent;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Transactional
class OrderStatusChangedListenerTest {

    @Autowired
    private OrderStatusChangedListener orderStatusChangedListener;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProcessedOrderStatusEventRepository processedOrderStatusEventRepository;

    @Test
    @DisplayName("apply a valid status transition and remember the processed event")
    void onOrderStatusChanged_appliesValidTransitionAndStoresIdempotencyRecord() {
        Order order = new Order();
        order.setOrderNumber("ORD-200");
        order.setUserId(42L);
        order.setStatus(OrderStatus.NEW);
        orderRepository.save(order);

        UUID eventId = UUID.randomUUID();
        OrderStatusChangedEvent event = new OrderStatusChangedEvent(
            eventId,
            java.time.Instant.now(),
            order.getId(),
            order.getOrderNumber(),
            OrderStatus.NEW.name(),
            OrderStatus.IN_PREPARATION.name()
        );

        orderStatusChangedListener.onOrderStatusChanged(event);

        Order updated = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(OrderStatus.IN_PREPARATION, updated.getStatus());
        assertEquals(true, processedOrderStatusEventRepository.existsByEventId(eventId));
    }

    @Test
    @DisplayName("ignore duplicate order status events")
    void onOrderStatusChanged_ignoresDuplicateEvent() {
        Order order = new Order();
        order.setOrderNumber("ORD-201");
        order.setUserId(43L);
        order.setStatus(OrderStatus.NEW);
        orderRepository.save(order);

        UUID eventId = UUID.randomUUID();
        processedOrderStatusEventRepository.save(new ProcessedOrderStatusEvent(eventId, order.getId()));

        OrderStatusChangedEvent event = new OrderStatusChangedEvent(
            eventId,
            java.time.Instant.now(),
            order.getId(),
            order.getOrderNumber(),
            OrderStatus.NEW.name(),
            OrderStatus.IN_PREPARATION.name()
        );

        orderStatusChangedListener.onOrderStatusChanged(event);

        Order unchanged = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(OrderStatus.NEW, unchanged.getStatus());
    }
}
