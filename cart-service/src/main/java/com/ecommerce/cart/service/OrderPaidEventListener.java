package com.ecommerce.cart.service;

import com.ecommerce.cart.entity.Cart;
import com.ecommerce.cart.repository.CartRepository;
import com.ecommerce.common.event.OrderPaidEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderPaidEventListener {

    private final CartRepository cartRepository;
    private final ProcessedOrderEventRepository processedOrderEventRepository;

    @KafkaListener(topics = "${app.kafka.topics.order-paid:order.paid}", groupId = "cart-service")
    @Transactional
    public void handleOrderPaid(OrderPaidEvent event) {
        if (event == null || event.getOrderId() == null) {
            log.warn("OrderPaidEvent invalide reçu par cart-service : {}", event);
            return;
        }

        if (processedOrderEventRepository.existsByOrderId(event.getOrderId())) {
            log.info("Événement OrderPaid déjà traité pour la commande {}", event.getOrderId());
            return;
        }

        if (event.getUserId() != null) {
            Optional<Cart> userCart = cartRepository.findByUserIdWithItems(event.getUserId());
            userCart.ifPresent(cart -> {
                cart.clearItems();
                cartRepository.save(cart);
            });
        }

        if (event.getSessionToken() != null && !event.getSessionToken().isBlank()) {
            Optional<Cart> guestCart = cartRepository.findBySessionTokenWithItems(event.getSessionToken());
            guestCart.ifPresent(cart -> {
                cart.clearItems();
                cartRepository.save(cart);
            });
        }

        processedOrderEventRepository.save(new ProcessedOrderEvent(event.getOrderId()));
        log.info("Panier nettoyé après paiement de la commande {} pour userId={} sessionToken={}",
            event.getOrderId(), event.getUserId(), event.getSessionToken());
    }
}
