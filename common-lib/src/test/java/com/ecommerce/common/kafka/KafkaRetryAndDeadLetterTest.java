package com.ecommerce.common.kafka;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import com.ecommerce.common.event.BackInStockEvent;
import com.ecommerce.common.event.CatalogEvent;
import com.ecommerce.common.event.OrderPaidEvent;
import com.ecommerce.common.event.PriceDroppedEvent;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the shared configuration the way a service gets it: only
 * spring.application.name and the broker address are set, everything else
 * comes from common-lib.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.application.name=kafka-test-service",
                "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
                // Short waits, same policy: 1 attempt + 2 retries.
                "app.kafka.retry.max-retries=2",
                "app.kafka.retry.initial-interval=50ms",
                "app.kafka.retry.max-interval=100ms"
        })
@EmbeddedKafka(partitions = 3, topics = {
        KafkaTopics.ORDER_PAID, KafkaTopics.ORDER_PAID + KafkaTopics.DLT_SUFFIX,
        KafkaTopics.CATALOG_EVENTS, KafkaTopics.CATALOG_EVENTS + KafkaTopics.DLT_SUFFIX
})
class KafkaRetryAndDeadLetterTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Autowired
    private TestListeners listeners;

    private Consumer<String, byte[]> dltConsumer;

    @AfterEach
    void tearDown() {
        if (dltConsumer != null) {
            dltConsumer.close();
        }
    }

    @Test
    void shouldDeliverASharedEvent() throws Exception {
        OrderPaidEvent sent = OrderPaidEvent.builder().orderId(1L).orderNumber("CMD-OK").userId(7L).build();

        kafkaTemplate.send(KafkaTopics.ORDER_PAID, sent.orderNumber(), sent).get();

        assertThat(listeners.orderPaid.poll(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isEqualTo(sent);
        assertThat(listeners.attempts("CMD-OK")).isEqualTo(1);
    }

    @Test
    void shouldDispatchEveryCatalogEventToOneListener() throws Exception {
        PriceDroppedEvent drop = PriceDroppedEvent.builder().variantId(10L).build();
        BackInStockEvent restock = BackInStockEvent.builder().variantId(10L).availableQuantity(3).build();

        kafkaTemplate.send(KafkaTopics.CATALOG_EVENTS, "10", drop).get();
        kafkaTemplate.send(KafkaTopics.CATALOG_EVENTS, "10", restock).get();

        assertThat(List.of(
                listeners.catalog.poll(TIMEOUT.toSeconds(), TimeUnit.SECONDS),
                listeners.catalog.poll(TIMEOUT.toSeconds(), TimeUnit.SECONDS)))
                .containsExactly("price dropped 10", "back in stock 10");
    }

    @Test
    void shouldRetryThenPublishToTheDeadLetterTopic() throws Exception {
        OrderPaidEvent failing = OrderPaidEvent.builder()
                .orderId(2L).orderNumber(TestListeners.ALWAYS_FAILS).build();
        dltConsumer = dltConsumer(KafkaTopics.ORDER_PAID + KafkaTopics.DLT_SUFFIX);

        kafkaTemplate.send(KafkaTopics.ORDER_PAID, failing.orderNumber(), failing).get();

        ConsumerRecord<String, byte[]> dead = deadLetter(TestListeners.ALWAYS_FAILS);
        assertThat(new String(dead.value())).contains("\"orderId\":2");
        assertThat(header(dead, KafkaHeaders.DLT_EXCEPTION_MESSAGE)).contains("listener failure");
        assertThat(header(dead, KafkaHeaders.DLT_ORIGINAL_TOPIC)).isEqualTo(KafkaTopics.ORDER_PAID);
        assertThat(listeners.attempts(TestListeners.ALWAYS_FAILS)).isEqualTo(3);
    }

    @Test
    void shouldSendAnUnreadableRecordToTheDeadLetterTopicWithoutRetrying() throws Exception {
        byte[] garbage = "{not json".getBytes();
        dltConsumer = dltConsumer(KafkaTopics.ORDER_PAID + KafkaTopics.DLT_SUFFIX);

        // Raw bytes, as a broken producer would send: not through the JSON template.
        try (Producer<String, byte[]> producer = new DefaultKafkaProducerFactory<>(
                KafkaTestUtils.producerProps(broker), new StringSerializer(), new ByteArraySerializer())
                .createProducer()) {
            producer.send(new ProducerRecord<>(KafkaTopics.ORDER_PAID, "poison", garbage)).get();
        }

        ConsumerRecord<String, byte[]> dead = deadLetter("poison");
        assertThat(dead.value()).isEqualTo(garbage);
        assertThat(header(dead, KafkaHeaders.DLT_EXCEPTION_FQCN))
                .isEqualTo("org.springframework.kafka.support.serializer.DeserializationException");
        assertThat(listeners.attempts("poison")).isZero();
    }

    /** The DLT is shared by the tests: find the record of this one by its key. */
    private ConsumerRecord<String, byte[]> deadLetter(String key) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            for (ConsumerRecord<String, byte[]> record : KafkaTestUtils.getRecords(dltConsumer, Duration.ofMillis(500))) {
                if (key.equals(record.key())) {
                    return record;
                }
            }
        }
        throw new AssertionError("No record with key " + key + " in the dead letter topic");
    }

    private Consumer<String, byte[]> dltConsumer(String topic) {
        Map<String, Object> props = KafkaTestUtils.consumerProps("dlt-reader-" + System.nanoTime(), "false", broker);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        Consumer<String, byte[]> consumer = new DefaultKafkaConsumerFactory<>(props,
                new StringDeserializer(), new ByteArrayDeserializer()).createConsumer();
        broker.consumeFromAnEmbeddedTopic(consumer, topic);
        return consumer;
    }

    private static String header(ConsumerRecord<?, ?> record, String name) {
        return new String(record.headers().lastHeader(name).value());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {

        @Bean
        TestListeners testListeners() {
            return new TestListeners();
        }
    }

    static class TestListeners {

        static final String ALWAYS_FAILS = "CMD-FAILS";

        final BlockingQueue<OrderPaidEvent> orderPaid = new LinkedBlockingQueue<>();
        final BlockingQueue<String> catalog = new LinkedBlockingQueue<>();
        private final List<String> calls = new CopyOnWriteArrayList<>();

        @KafkaListener(topics = "${app.kafka.topics.order-paid}")
        void onOrderPaid(OrderPaidEvent event) {
            calls.add(event.orderNumber());
            if (ALWAYS_FAILS.equals(event.orderNumber())) {
                throw new IllegalStateException("listener failure");
            }
            orderPaid.add(event);
        }

        @KafkaListener(topics = "${app.kafka.topics.catalog-events}")
        void onCatalogEvent(CatalogEvent event) {
            catalog.add(switch (event) {
                case PriceDroppedEvent drop -> "price dropped " + drop.variantId();
                case BackInStockEvent restock -> "back in stock " + restock.variantId();
            });
        }

        int attempts(String orderNumber) {
            AtomicInteger count = new AtomicInteger();
            calls.forEach(call -> {
                if (call.equals(orderNumber)) {
                    count.incrementAndGet();
                }
            });
            return count.get();
        }
    }
}
