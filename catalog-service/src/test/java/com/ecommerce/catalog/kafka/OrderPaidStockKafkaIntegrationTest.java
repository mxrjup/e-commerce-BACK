package com.ecommerce.catalog.kafka;

import com.ecommerce.catalog.demo.DemoCatalogLoader;
import com.ecommerce.catalog.repository.ProcessedOrderRepository;
import com.ecommerce.common.event.OrderPaidEvent;
import com.ecommerce.common.kafka.KafkaTopics;
import com.ecommerce.test.kafka.KafkaIntegrationTest;
import com.ecommerce.test.kafka.KafkaTestRecords;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * The catalogue's consumer of {@code order.paid} (C21) against a real broker:
 * the listener, the shared JSON configuration and the retry and DLT policy
 * of common-lib, end to end. The broker is shared by all the tests, so each
 * order has its own id.
 */
@ActiveProfiles("demo")
class OrderPaidStockKafkaIntegrationTest extends KafkaIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    /** The H2 tests switch the listener off; here it has a broker to consume from. */
    @DynamicPropertySource
    static void listener(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.listener.auto-startup", () -> "true");
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:catalogkafka;MODE=PostgreSQL;"
                + "DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
    }

    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Autowired
    private ProcessedOrderRepository processedOrderRepository;

    @Autowired
    private DemoCatalogLoader loader;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void loadIfEmpty() {
        loader.run(null);
    }

    @Test
    void shouldTakeAPaidOrderOutOfTheStock() throws Exception {
        int before = stockOf("CRUC-DDR5-6000-32");
        OrderPaidEvent paid = paidOrder(uniqueId(), "CRUC-DDR5-6000-32", 3);

        publish(paid);

        await().atMost(TIMEOUT).untilAsserted(() -> {
            assertThat(processedOrderRepository.existsById(paid.orderId())).isTrue();
            assertThat(stockOf("CRUC-DDR5-6000-32")).isEqualTo(before - 3);
        });
    }

    @Test
    void shouldIgnoreASecondPublicationOfTheSamePaidOrder() throws Exception {
        int before = stockOf("SAMS-990PRO-1T");
        OrderPaidEvent first = paidOrder(uniqueId(), "SAMS-990PRO-1T", 2);
        publish(first);
        await().atMost(TIMEOUT).until(() -> processedOrderRepository.existsById(first.orderId()));

        OrderPaidEvent duplicate = paidOrder(first.orderId(), "SAMS-990PRO-1T", 2);
        assertThat(duplicate.eventId()).isNotEqualTo(first.eventId());
        publish(duplicate);
        // Meme cle, meme partition : une fois celui-ci traite, le doublon l'est aussi.
        OrderPaidEvent marker = OrderPaidEvent.builder()
                .orderId(uniqueId()).orderNumber(first.orderNumber()).build();
        publish(marker);
        await().atMost(TIMEOUT).until(() -> processedOrderRepository.existsById(marker.orderId()));

        assertThat(stockOf("SAMS-990PRO-1T")).isEqualTo(before - 2);
        // Ignore comme deja traite, pas en echec : rien dans la DLT.
        assertThatThrownBy(() -> KafkaTestRecords.awaitDeadLetter(
                KafkaTopics.ORDER_PAID, first.orderNumber(), Duration.ofSeconds(3)))
                .isInstanceOf(AssertionError.class);
    }

    private OrderPaidEvent paidOrder(long orderId, String sku, int quantity) {
        return OrderPaidEvent.builder()
                .orderId(orderId)
                .orderNumber("CMD-TEST-" + orderId)
                .userId(1L)
                .items(List.of(new OrderPaidEvent.Item(idOf(sku), quantity)))
                .build();
    }

    private void publish(OrderPaidEvent event) throws Exception {
        kafkaTemplate.send(KafkaTopics.ORDER_PAID, event.orderNumber(), event).get(10, TimeUnit.SECONDS);
    }

    private static long uniqueId() {
        return ThreadLocalRandom.current().nextLong(1_000_000, Long.MAX_VALUE);
    }

    private long idOf(String sku) {
        return jdbcTemplate.queryForObject("SELECT id FROM variante WHERE sku = ?", Long.class, sku);
    }

    private int stockOf(String sku) {
        return jdbcTemplate.queryForObject("SELECT stock FROM variante WHERE sku = ?", Integer.class, sku);
    }
}
