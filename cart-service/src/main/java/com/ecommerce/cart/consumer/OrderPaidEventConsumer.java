package com.ecommerce.cart.consumer;
import com.ecommerce.cart.event.OrderPaidEvent;
import com.ecommerce.cart.service.CartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;






@Component
@RequiredArgsConstructor
@Slf4j
public class OrderPaidEventConsumer {

    private final CartService cartService;

    @KafkaListener(
        topics = "${app.kafka.topics.order-paid:order.paid}",
        groupId = "${spring.kafka.consumer.group-id:cart-service}"
    )
    public void handleOrderPaid(OrderPaidEvent event){
        log.info("received order paid event for orderId : {}, userId: {}", event.getOrderId(), event.getUserId());
        cartService.clearCartOnOrderPaid(event.getUserId(), event.getSessionToken());
    }
  
}
