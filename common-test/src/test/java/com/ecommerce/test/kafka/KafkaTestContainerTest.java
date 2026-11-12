package com.ecommerce.test.kafka;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;

import com.ecommerce.common.event.OrderPaidEvent;
import com.ecommerce.common.kafka.KafkaTopics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Checks the template itself, against the real broker: topics, disabled
 * auto-creation, and the shared retry and DLT policy.
 */
@SpringBootTest(properties = "spring.application.name=common-test")
class KafkaTestContainerTest extends KafkaIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Autowired
    private FailingListener listener;

    @Test
    void shouldCreateExactlyTheTopicsOfTheScript() throws Exception {
        try (AdminClient admin = AdminClient.create(
                Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaTestContainer.bootstrapServers()))) {
            Set<String> topics = admin.listTopics().names().get(10, TimeUnit.SECONDS);

            assertThat(topics).contains(
                    KafkaTopics.ORDER_PAID, KafkaTopics.deadLetterTopicOf(KafkaTopics.ORDER_PAID),
                    KafkaTopics.CATALOG_EVENTS, KafkaTopics.deadLetterTopicOf(KafkaTopics.CATALOG_EVENTS),
                    KafkaTopics.SUPPORT_ADMIN_REPLY, KafkaTopics.deadLetterTopicOf(KafkaTopics.SUPPORT_ADMIN_REPLY));
            assertThat(topics).allMatch(topic -> topic.startsWith("__") || topic.contains("."));
        }
    }

    @Test
    void shouldRefuseATopicThatTheScriptDoesNotCreate() {
        // Same failure as in production: the broker does not create topics on demand.
        assertThatThrownBy(() -> KafkaTestRecords.sendRaw("topic.absent", "key", "value".getBytes()))
                .hasRootCauseMessage("Topic topic.absent not present in metadata after 10000 ms.");
    }

    @Test
    void shouldSendAFailingRecordToItsDeadLetterTopic() throws Exception {
        OrderPaidEvent event = OrderPaidEvent.builder().orderId(1L).orderNumber("CMD-TEMPLATE-FAIL").userId(1L).build();

        kafkaTemplate.send(KafkaTopics.ORDER_PAID, event.orderNumber(), event).get(10, TimeUnit.SECONDS);

        ConsumerRecord<String, byte[]> dead = KafkaTestRecords.awaitDeadLetter(
                KafkaTopics.ORDER_PAID, event.orderNumber(), TIMEOUT);
        assertThat(KafkaTestRecords.header(dead, KafkaHeaders.DLT_EXCEPTION_MESSAGE)).contains("always fails");
        // 1 attempt + the 3 default retries, with the short waits of the template.
        assertThat(listener.attempts).isEqualTo(4);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {

        @Bean
        FailingListener failingListener() {
            return new FailingListener();
        }
    }

    static class FailingListener {

        volatile int attempts;

        @KafkaListener(topics = "${app.kafka.topics.order-paid}")
        void on(OrderPaidEvent event) {
            attempts++;
            throw new IllegalStateException("always fails");
        }
    }
}
