package com.ecommerce.cart.kafka;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;

import com.ecommerce.cart.entity.Cart;
import com.ecommerce.cart.entity.CartItem;
import com.ecommerce.cart.repository.CartRepository;
import com.ecommerce.cart.service.ProcessedOrderEventRepository;
import com.ecommerce.common.event.OrderPaidEvent;
import com.ecommerce.common.kafka.KafkaTopics;
import com.ecommerce.test.kafka.KafkaIntegrationTest;
import com.ecommerce.test.kafka.KafkaTestRecords;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * The cart's consumer of {@code order.paid} (P06) against a real broker: the
 * listener, the shared JSON configuration of common-lib, and the retry and
 * DLT policy, end to end.
 *
 * <p>Copy this class to test another consumer: extend
 * {@link KafkaIntegrationTest}, publish with the application's
 * {@link KafkaTemplate}, and wait for the effect with Awaitility. Each test
 * uses its own identifiers, because the broker is shared by all the tests.
 */
class OrderPaidKafkaIntegrationTest extends KafkaIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private ProcessedOrderEventRepository processedOrderEventRepository;

    @Test
    void shouldEmptyTheCartOfAPaidOrder() throws Exception {
        long userId = uniqueId();
        cartWithOneItem(userId);
        OrderPaidEvent event = paidOrder(uniqueId(), userId);

        publish(event);

        await().atMost(TIMEOUT).untilAsserted(() -> {
            assertThat(processedOrderEventRepository.existsByOrderId(event.orderId())).isTrue();
            assertThat(itemsInCartOf(userId)).isZero();
        });
    }

    @Test
    void shouldIgnoreASecondPublicationOfTheSamePaidOrder() throws Exception {
        long userId = uniqueId();
        OrderPaidEvent first = paidOrder(uniqueId(), userId);
        publish(first);
        await().atMost(TIMEOUT).until(() -> processedOrderEventRepository.existsByOrderId(first.orderId()));

        // The customer fills a new cart, then Stripe calls the webhook again:
        // same order, new eventId. The new cart must survive.
        cartWithOneItem(userId);
        OrderPaidEvent duplicate = paidOrder(first.orderId(), userId);
        assertThat(duplicate.eventId()).isNotEqualTo(first.eventId());
        publish(duplicate);

        // Same key, same partition: once this one is processed, the duplicate was too.
        OrderPaidEvent marker = OrderPaidEvent.builder()
                .orderId(uniqueId()).orderNumber(first.orderNumber()).userId(uniqueId()).build();
        publish(marker);
        await().atMost(TIMEOUT).until(() -> processedOrderEventRepository.existsByOrderId(marker.orderId()));

        assertThat(itemsInCartOf(userId)).isEqualTo(1);
        // Skipped as already processed, not failed: the unique constraint on
        // order_id would also keep the cart, but only after 4 failed
        // attempts and a trip to the DLT.
        assertThatThrownBy(() -> KafkaTestRecords.awaitDeadLetter(
                KafkaTopics.ORDER_PAID, first.orderNumber(), Duration.ofSeconds(3)))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void shouldSendAnUnreadableRecordToTheDeadLetterTopicWithoutBlockingThePartition() throws Exception {
        long userId = uniqueId();
        cartWithOneItem(userId);
        OrderPaidEvent next = paidOrder(uniqueId(), userId);
        byte[] garbage = "{not json".getBytes();

        // A broken producer, then a valid event behind it on the same partition.
        KafkaTestRecords.sendRaw(KafkaTopics.ORDER_PAID, next.orderNumber(), garbage);
        publish(next);

        ConsumerRecord<String, byte[]> dead =
                KafkaTestRecords.awaitDeadLetter(KafkaTopics.ORDER_PAID, next.orderNumber(), TIMEOUT);
        assertThat(dead.value()).isEqualTo(garbage);
        assertThat(KafkaTestRecords.header(dead, KafkaHeaders.DLT_EXCEPTION_FQCN))
                .isEqualTo("org.springframework.kafka.support.serializer.DeserializationException");
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(itemsInCartOf(userId)).isZero());
    }

    private void publish(OrderPaidEvent event) throws Exception {
        kafkaTemplate.send(KafkaTopics.ORDER_PAID, event.orderNumber(), event).get(10, TimeUnit.SECONDS);
    }

    private void cartWithOneItem(long userId) {
        Cart cart = cartRepository.findByUserIdWithItems(userId).orElseGet(() -> new Cart(userId, null));
        cart.addItem(new CartItem(cart, 5L, 2));
        cartRepository.save(cart);
    }

    private int itemsInCartOf(long userId) {
        return cartRepository.findByUserIdWithItems(userId).map(cart -> cart.getItems().size()).orElse(0);
    }

    private static OrderPaidEvent paidOrder(long orderId, long userId) {
        return OrderPaidEvent.builder().orderId(orderId).orderNumber("CMD-IT-" + orderId).userId(userId).build();
    }

    private static long uniqueId() {
        return ThreadLocalRandom.current().nextLong(1_000_000, Long.MAX_VALUE);
    }
}
