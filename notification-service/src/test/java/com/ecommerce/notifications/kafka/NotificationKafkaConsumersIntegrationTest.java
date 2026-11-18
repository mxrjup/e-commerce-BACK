package com.ecommerce.notifications.kafka;

import com.ecommerce.common.event.BackInStockEvent;
import com.ecommerce.common.event.OrderPaidEvent;
import com.ecommerce.common.event.OrderStatusChangedEvent;
import com.ecommerce.common.event.PriceDroppedEvent;
import com.ecommerce.common.event.SupportAdminReplyEvent;
import com.ecommerce.common.kafka.KafkaTopics;
import com.ecommerce.notifications.client.UserContactResolver;
import com.ecommerce.notifications.client.UserContactResolver.UserContact;
import com.ecommerce.notifications.dto.NotificationResponseDto;
import com.ecommerce.notifications.entity.CatalogAlertSubscription;
import com.ecommerce.notifications.entity.CatalogAlertType;
import com.ecommerce.notifications.entity.Notification;
import com.ecommerce.notifications.entity.NotificationType;
import com.ecommerce.notifications.repository.CatalogAlertSubscriptionRepository;
import com.ecommerce.notifications.repository.NotificationRepository;
import com.ecommerce.notifications.repository.ProcessedKafkaEventRepository;
import com.ecommerce.notifications.service.NotificationService;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        KafkaTopics.ORDER_STATUS_CHANGED, KafkaTopics.ORDER_STATUS_CHANGED + KafkaTopics.DLT_SUFFIX,
        KafkaTopics.CATALOG_EVENTS, KafkaTopics.CATALOG_EVENTS + KafkaTopics.DLT_SUFFIX,
        KafkaTopics.SUPPORT_ADMIN_REPLY, KafkaTopics.SUPPORT_ADMIN_REPLY + KafkaTopics.DLT_SUFFIX
})
class NotificationKafkaConsumersIntegrationTest {

    private static final String KEYCLOAK_SUB = "7f8b1c2d-4242-4242-8888-abcdef123456";

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private KafkaListenerEndpointRegistry registry;

    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ProcessedKafkaEventRepository processedKafkaEventRepository;

    @Autowired
    private NotificationService notificationService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private UserContactResolver userContactResolver;

    @MockBean
    private JavaMailSender mailSender;

    @Autowired
    private CatalogAlertSubscriptionRepository catalogAlertSubscriptionRepository;

    @BeforeEach
    void setUp() {
        for (MessageListenerContainer container : registry.getListenerContainers()) {
            ContainerTestUtils.waitForAssignment(container, embeddedKafkaBroker.getPartitionsPerTopic());
        }
        notificationRepository.deleteAll();
        catalogAlertSubscriptionRepository.deleteAll();
        processedKafkaEventRepository.deleteAll();
        reset(mailSender);
        when(mailSender.createMimeMessage()).thenAnswer(invocation ->
                new MimeMessage(Session.getInstance(new Properties())));
        when(userContactResolver.resolveUserContact(42L)).thenReturn(Optional.of(
                new UserContact(42L, KEYCLOAK_SUB, "alice@example.com", "Alice")
        ));
    }

    @Test
    void shouldCreateOrderNotificationAndSendEmailWhenOrderPaidEventReceived() throws Exception {
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
            assertThat(notificationRepository.findByRecipientIdOrderByCreatedAtDesc("42")).isEmpty();

            List<Notification> saved = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(KEYCLOAK_SUB);
            assertThat(saved).hasSize(1);
            assertThat(saved.getFirst().getType()).isEqualTo(NotificationType.ORDER_UPDATE);
            assertThat(saved.getFirst().getTitle()).isEqualTo("Commande confirmée");
            assertThat(saved.getFirst().getMessage()).contains("CMD-2026-000101");

            List<NotificationResponseDto> userNotifications = notificationService.getUserNotifications(KEYCLOAK_SUB, false);
            assertThat(userNotifications).hasSize(1);
            assertThat(userNotifications.getFirst().getTitle()).isEqualTo("Commande confirmée");

            verify(mailSender, times(1)).send(any(MimeMessage.class));
        });

        // Rejeu du même eventId : doit être ignoré (idempotence notification + email)
        kafkaTemplate.send(KafkaTopics.ORDER_PAID, event.orderNumber(), event).get();
        Thread.sleep(300);
        assertThat(notificationService.getUserNotifications(KEYCLOAK_SUB, false)).hasSize(1);
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void shouldCreateShipmentNotificationAndSendEmailWhenOrderShippedEventReceived() throws Exception {
        OrderStatusChangedEvent event = OrderStatusChangedEvent.builder()
                .orderId(102L)
                .orderNumber("CMD-2026-000102")
                .userId(42L)
                .previousStatus("IN_PREPARATION")
                .newStatus("SHIPPED")
                .build();

        kafkaTemplate.send(KafkaTopics.ORDER_STATUS_CHANGED, event.orderNumber(), event).get();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<NotificationResponseDto> userNotifications = notificationService.getUserNotifications(KEYCLOAK_SUB, false);
            assertThat(userNotifications).hasSize(1);
            assertThat(userNotifications.getFirst().getType()).isEqualTo(NotificationType.ORDER_UPDATE);
            assertThat(userNotifications.getFirst().getTitle()).isEqualTo("Commande expédiée");
            assertThat(userNotifications.getFirst().getMessage()).contains("CMD-2026-000102");

            verify(mailSender, times(1)).send(any(MimeMessage.class));
        });
    }

    @Test
    void shouldCreateSupportNotificationAndSendEmailWhenSupportAdminReplyEventReceived() throws Exception {
        SupportAdminReplyEvent event = SupportAdminReplyEvent.builder()
                .ticketId(55L)
                .userId(42L)
                .ticketSubject("Colis endommagé")
                .build();

        kafkaTemplate.send(KafkaTopics.SUPPORT_ADMIN_REPLY, String.valueOf(event.ticketId()), event).get();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(notificationRepository.findByRecipientIdOrderByCreatedAtDesc("42")).isEmpty();

            List<NotificationResponseDto> userNotifications = notificationService.getUserNotifications(KEYCLOAK_SUB, false);
            assertThat(userNotifications).hasSize(1);
            assertThat(userNotifications.getFirst().getType()).isEqualTo(NotificationType.SYSTEM);
            assertThat(userNotifications.getFirst().getMessage()).contains("#55").contains("Colis endommagé");

            verify(mailSender, times(1)).send(any(MimeMessage.class));
        });
    }

    @Test
    void shouldTriggerMatchingCatalogAlertsAndDeactivateTriggeredSubscriptions() throws Exception {
        CatalogAlertSubscription alertUserA = catalogAlertSubscriptionRepository.save(
                CatalogAlertSubscription.builder()
                        .recipientId("user-a")
                        .productId(10L)
                        .variantId(20L)
                        .alertType(CatalogAlertType.PRICE_DROP)
                        .targetPriceExclTax(new BigDecimal("40.00"))
                        .active(true)
                        .build()
        );
        CatalogAlertSubscription alertUserB = catalogAlertSubscriptionRepository.save(
                CatalogAlertSubscription.builder()
                        .recipientId("user-b")
                        .productId(10L)
                        .variantId(20L)
                        .alertType(CatalogAlertType.PRICE_DROP)
                        .targetPriceExclTax(new BigDecimal("25.00"))
                        .active(true)
                        .build()
        );
        CatalogAlertSubscription alertUserC = catalogAlertSubscriptionRepository.save(
                CatalogAlertSubscription.builder()
                        .recipientId("user-c")
                        .productId(10L)
                        .variantId(21L)
                        .alertType(CatalogAlertType.BACK_IN_STOCK)
                        .active(true)
                        .build()
        );

        PriceDroppedEvent priceDrop = PriceDroppedEvent.builder()
                .productId(10L)
                .variantId(20L)
                .previousUnitPriceExclTax(new BigDecimal("50.00"))
                .newUnitPriceExclTax(new BigDecimal("35.00"))
                .build();

        kafkaTemplate.send(KafkaTopics.CATALOG_EVENTS, "20", priceDrop).get();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(processedKafkaEventRepository.existsById(priceDrop.eventId())).isTrue();

            // user-a (cible 40.00 >= 35.00) : notifié et alerte désactivée
            List<Notification> userANotifs = notificationRepository.findByRecipientIdOrderByCreatedAtDesc("user-a");
            assertThat(userANotifs).hasSize(1);
            assertThat(userANotifs.getFirst().getType()).isEqualTo(NotificationType.VARIANT_ALERT);

            CatalogAlertSubscription updatedA = catalogAlertSubscriptionRepository.findById(alertUserA.getId()).orElseThrow();
            assertThat(updatedA.isActive()).isFalse();
            assertThat(updatedA.getTriggeredAt()).isNotNull();

            // user-b (cible 25.00 < 35.00) : non notifié et alerte toujours active
            assertThat(notificationRepository.findByRecipientIdOrderByCreatedAtDesc("user-b")).isEmpty();
            CatalogAlertSubscription updatedB = catalogAlertSubscriptionRepository.findById(alertUserB.getId()).orElseThrow();
            assertThat(updatedB.isActive()).isTrue();
            assertThat(updatedB.getTriggeredAt()).isNull();
        });

        BackInStockEvent backInStock = BackInStockEvent.builder()
                .productId(10L)
                .variantId(21L)
                .availableQuantity(12)
                .build();

        kafkaTemplate.send(KafkaTopics.CATALOG_EVENTS, "21", backInStock).get();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(processedKafkaEventRepository.existsById(backInStock.eventId())).isTrue();

            // user-c : notifié et alerte désactivée
            List<Notification> userCNotifs = notificationRepository.findByRecipientIdOrderByCreatedAtDesc("user-c");
            assertThat(userCNotifs).hasSize(1);
            assertThat(userCNotifs.getFirst().getType()).isEqualTo(NotificationType.VARIANT_ALERT);

            CatalogAlertSubscription updatedC = catalogAlertSubscriptionRepository.findById(alertUserC.getId()).orElseThrow();
            assertThat(updatedC.isActive()).isFalse();
            assertThat(updatedC.getTriggeredAt()).isNotNull();
        });
    }
}