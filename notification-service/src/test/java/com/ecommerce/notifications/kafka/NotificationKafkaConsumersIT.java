package com.ecommerce.notifications.kafka;

import com.ecommerce.common.event.BackInStockEvent;
import com.ecommerce.common.event.OrderPaidEvent;
import com.ecommerce.common.event.PriceDroppedEvent;
import com.ecommerce.common.event.SupportAdminReplyEvent;
import com.ecommerce.common.kafka.KafkaTopics;
import com.ecommerce.notifications.entity.Notification;
import com.ecommerce.notifications.entity.NotificationType;
import com.ecommerce.notifications.repository.NotificationRepository;
import com.ecommerce.notifications.repository.ProcessedKafkaEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
                "app.kafka.retry.max-retries=1",
                "app.kafka.retry.initial-interval=50ms"
        }
)
@ActiveProfiles("local-h2")
@EmbeddedKafka(partitions = 3, topics = {
        KafkaTopics.ORDER_PAID, KafkaTopics.ORDER_PAID + KafkaTopics.DLT_SUFFIX,
        KafkaTopics.CATALOG_EVENTS, KafkaTopics.CATALOG_EVENTS + KafkaTopics.DLT_SUFFIX,
        KafkaTopics.SUPPORT_ADMIN_REPLY, KafkaTopics.SUPPORT_ADMIN_REPLY + KafkaTopics.DLT_SUFFIX
})
class NotificationKafkaConsumersIT {

    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ProcessedKafkaEventRepository processedKafkaEventRepository;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        processedKafkaEventRepository.deleteAll();
    }

    @Test
    void shouldCreateOrderNotificationWhenOrderPaidEventReceived() throws Exception {
        UUID eventId = UUID.randomUUID();
        OrderPaidEvent event = OrderPaidEvent.builder()
                .eventId(eventId)
                .orderId(101L)
                .orderNumber("CMD-2026-000101")
                .userId(42L)
                .totalInclTax(new BigDecimal("89.90"))
                .build();

        kafkaTemplate.send(KafkaTopics.ORDER_PAID, event.orderNumber(), event).get();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<Notification> notifications = notificationRepository.findByRecipientIdOrderByCreatedAtDesc("42");
            assertThat(notifications).hasSize(1);
            assertThat(notifications.getFirst().getType()).isEqualTo(NotificationType.ORDER_UPDATE);
            assertThat(notifications.getFirst().getTitle()).isEqualTo("Commande confirmée");
            assertThat(notifications.getFirst().getMessage()).contains("CMD-2026-000101");
        });

        // Rejeu du même eventId : doit être ignoré (idempotence)
        kafkaTemplate.send(KafkaTopics.ORDER_PAID, event.orderNumber(), event).get();
        Thread.sleep(300);
        assertThat(notificationRepository.findByRecipientIdOrderByCreatedAtDesc("42")).hasSize(1);
    }

    @Test
    void shouldCreateSupportNotificationWhenSupportAdminReplyEventReceived() throws Exception {
        SupportAdminReplyEvent event = SupportAdminReplyEvent.builder()
                .ticketId(55L)
                .userId(42L)
                .ticketSubject("Colis endommagé")
                .build();

        kafkaTemplate.send(KafkaTopics.SUPPORT_ADMIN_REPLY, String.valueOf(event.ticketId()), event).get();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<Notification> notifications = notificationRepository.findByRecipientIdOrderByCreatedAtDesc("42");
            assertThat(notifications).hasSize(1);
            assertThat(notifications.getFirst().getType()).isEqualTo(NotificationType.SYSTEM);
            assertThat(notifications.getFirst().getMessage()).contains("#55").contains("Colis endommagé");
        });
    }

    @Test
    void shouldConsumeCatalogEventsWithoutError() throws Exception {
        PriceDroppedEvent drop = PriceDroppedEvent.builder()
                .productId(10L)
                .variantId(20L)
                .previousUnitPriceExclTax(new BigDecimal("50.00"))
                .newUnitPriceExclTax(new BigDecimal("35.00"))
                .build();
        BackInStockEvent restock = BackInStockEvent.builder()
                .productId(10L)
                .variantId(21L)
                .availableQuantity(12)
                .build();

        kafkaTemplate.send(KafkaTopics.CATALOG_EVENTS, "20", drop).get();
        kafkaTemplate.send(KafkaTopics.CATALOG_EVENTS, "21", restock).get();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(processedKafkaEventRepository.existsById(drop.eventId())).isTrue();
            assertThat(processedKafkaEventRepository.existsById(restock.eventId())).isTrue();
        });
    }
}