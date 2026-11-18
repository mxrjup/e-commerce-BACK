package com.ecommerce.notifications.kafka;

import com.ecommerce.common.event.BackInStockEvent;
import com.ecommerce.common.event.CatalogEvent;
import com.ecommerce.common.event.OrderPaidEvent;
import com.ecommerce.common.event.PriceDroppedEvent;
import com.ecommerce.common.event.SupportAdminReplyEvent;
import com.ecommerce.common.kafka.KafkaTopics;
import com.ecommerce.notifications.client.UserSubjectResolver;
import com.ecommerce.notifications.entity.NotificationType;
import com.ecommerce.notifications.service.CatalogAlertService;
import com.ecommerce.notifications.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationKafkaConsumers {

    private final NotificationService notificationService;
    private final CatalogAlertService catalogAlertService;
    private final UserSubjectResolver userSubjectResolver;

    @KafkaListener(topics = "${app.kafka.topics.order-paid:" + KafkaTopics.ORDER_PAID + "}")
    public void onOrderPaid(OrderPaidEvent event) {
        Optional<String> recipientSub = userSubjectResolver.resolveKeycloakSubject(event.userId());
        if (recipientSub.isEmpty()) {
            log.info("Commande ignorée pour les notifications (orderId={}, orderNumber={}, userId={})",
                    event.orderId(), event.orderNumber(), event.userId());
            notificationService.markEventProcessed(event.eventId(), KafkaTopics.ORDER_PAID);
            return;
        }

        String amountText = event.totalInclTax() != null
                ? " d'un montant de " + NumberFormat.getCurrencyInstance(Locale.FRANCE).format(event.totalInclTax())
                : "";

        notificationService.createFromEvent(
                event.eventId(),
                KafkaTopics.ORDER_PAID,
                recipientSub.get(),
                NotificationType.ORDER_UPDATE,
                "Commande confirmée",
                "Votre commande " + event.orderNumber() + amountText + " a bien été payée et est en cours de préparation."
        );
    }

    @KafkaListener(topics = "${app.kafka.topics.catalog-events:" + KafkaTopics.CATALOG_EVENTS + "}")
    public void onCatalogEvent(CatalogEvent event) {
        switch (event) {
            case PriceDroppedEvent drop -> {
                int count = catalogAlertService.handlePriceDropped(drop);
                log.info("PriceDroppedEvent traité (variantId={}, notificationsCréées={})",
                        drop.variantId(), count);
            }
            case BackInStockEvent restock -> {
                int count = catalogAlertService.handleBackInStock(restock);
                log.info("BackInStockEvent traité (variantId={}, notificationsCréées={})",
                        restock.variantId(), count);
            }
        }
    }

    @KafkaListener(topics = "${app.kafka.topics.support-admin-reply:" + KafkaTopics.SUPPORT_ADMIN_REPLY + "}")
    public void onSupportAdminReply(SupportAdminReplyEvent event) {
        Optional<String> recipientSub = userSubjectResolver.resolveKeycloakSubject(event.userId());
        if (recipientSub.isEmpty()) {
            log.warn("Réponse SAV ignorée car aucun subject Keycloak résolu pour userId={} (ticketId={})",
                    event.userId(), event.ticketId());
            notificationService.markEventProcessed(event.eventId(), KafkaTopics.SUPPORT_ADMIN_REPLY);
            return;
        }

        String subjectPart = (event.ticketSubject() != null && !event.ticketSubject().isBlank())
                ? " (« " + event.ticketSubject() + " »)"
                : "";

        notificationService.createFromEvent(
                event.eventId(),
                KafkaTopics.SUPPORT_ADMIN_REPLY,
                recipientSub.get(),
                NotificationType.SYSTEM,
                "Nouvelle réponse du support",
                "Un conseiller a répondu à votre demande SAV #" + event.ticketId() + subjectPart + "."
        );
    }
}