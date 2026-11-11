package com.ecommerce.cart.service;

import com.ecommerce.cart.entity.Cart;
import com.ecommerce.cart.entity.ProcessedOrderEvent;
import com.ecommerce.cart.repository.CartRepository;
import com.ecommerce.cart.repository.ProcessedOrderEventRepository;
import com.ecommerce.common.event.OrderPaidEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class OrderPaidEventListenerTest {

    @Autowired
    private OrderPaidEventListener orderPaidEventListener;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private ProcessedOrderEventRepository processedOrderEventRepository;

    @Test
    @DisplayName("clear guest and user carts when an order is paid and remember the processed order")
    void handleOrderPaid_clearsRelevantCartsAndStoresIdempotencyRecord() {
        cartRepository.save(new Cart(null, "guest-token-42"));
        cartRepository.save(new Cart(10L, null));

        orderPaidEventListener.handleOrderPaid(
            OrderPaidEvent.builder()
                .orderId(99L)
                .orderNumber("CMD-2026-000001")
                .userId(10L)
                .sessionToken("guest-token-42")
                .build()
        );

        assertTrue(cartRepository.findBySessionTokenWithItems("guest-token-42").orElseThrow().getItems().isEmpty());
        assertTrue(cartRepository.findByUserIdWithItems(10L).orElseThrow().getItems().isEmpty());
        assertTrue(processedOrderEventRepository.existsByOrderId(99L));
    }

    @Test
    @DisplayName("ignore duplicate order-paid events")
    void handleOrderPaid_ignoresDuplicateOrderId() {
        cartRepository.save(new Cart(null, "guest-token-77"));
        processedOrderEventRepository.save(new ProcessedOrderEvent(456L));

        orderPaidEventListener.handleOrderPaid(
            OrderPaidEvent.builder()
                .orderId(456L)
                .orderNumber("CMD-2026-000456")
                .sessionToken("guest-token-77")
                .build()
        );

        assertTrue(cartRepository.findBySessionTokenWithItems("guest-token-77").orElseThrow().getItems().isEmpty());
    }
}
