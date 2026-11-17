package com.ecommerce.notifications.kafka;

import com.ecommerce.common.event.BackInStockEvent;
import com.ecommerce.common.event.CatalogEvent;
import com.ecommerce.common.event.OrderPaidEvent;
import com.ecommerce.common.event.PriceDroppedEvent;
import com.ecommerce.common.event.SupportAdminReplyEvent;
import com.ecommerce.common.kafka.KafkaTopics;
import com.ecommerce.notifications.entity.NotificationType;
import com.ecommerce.notifications.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationKafkaConsumers {

    private final NotificationService notificationService;

    /**
     * Topic 1 : Commande payée (order.paid / O03).
     */
    @KafkaListener(topics = "${app.kafka.topics.order-paid:" + KafkaTopics.ORDER_PAID + "}")
    public void onOrderPaid(OrderPaidEvent event) {
        if (event.userId() == null) {
            log.info("Commande invité payée ignorée pour les notifications (orderId={}, orderNumber={})",
                    event.orderId(), event.orderNumber());
            notificationService.markEventProcessed(event.eventId(), KafkaTopics.ORDER_PAID);
            return;
        }

        String amountText = event.totalInclTax() != null
                ? " d'un montant de " + NumberFormat.getCurrencyInstance(Locale.FRANCE).format(event.totalInclTax())
                : "";

        notificationService.createFromEvent(
                event.eventId(),
                KafkaTopics.ORDER_PAID,
                String.valueOf(event.userId()),
                NotificationType.ORDER_UPDATE,
                "Commande confirmée",
                "Votre commande " + event.orderNumber() + amountText + " a bien été payée et est en cours de préparation."
        );
    }

    /**
     * Topic 2 : Événements catalogue (catalogue.evenements / C09).
     * Switch exhaustif sur l'interface scellée CatalogEvent (PriceDroppedEvent, BackInStockEvent).
     */
    @KafkaListener(topics = "${app.kafka.topics.catalog-events:" + KafkaTopics.CATALOG_EVENTS + "}")
    public void onCatalogEvent(CatalogEvent event) {
        switch (event) {
            case PriceDroppedEvent drop -> {
                log.info("Événement catalogue reçu : baisse de prix sur la variante {} ({} -> {})",
                        drop.variantId(), drop.previousUnitPriceExclTax(), drop.newUnitPriceExclTax());
                // Les abonnements par variante et génération des notifications associées sont traités dans N03.
                notificationService.markEventProcessed(drop.eventId(), KafkaTopics.CATALOG_EVENTS);
            }
            case BackInStockEvent restock -> {
                log.info("Événement catalogue reçu : retour en stock sur la variante {} (quantité={})",
                        restock.variantId(), restock.availableQuantity());
                // Les abonnements par variante et génération des notifications associées sont traités dans N03.
                notificationService.markEventProcessed(restock.eventId(), KafkaTopics.CATALOG_EVENTS);
            }
        }
    }

    /**
     * Topic 3 : Réponse d'un collaborateur sur un ticket SAV (sav.reponse-admin / S03).
     */
    @KafkaListener(topics = "${app.kafka.topics.support-admin-reply:" + KafkaTopics.SUPPORT_ADMIN_REPLY + "}")
    public void onSupportAdminReply(SupportAdminReplyEvent event) {
        String subjectPart = (event.ticketSubject() != null && !event.ticketSubject().isBlank())
                ? " (« " + event.ticketSubject() + " »)"
                : "";

        notificationService.createFromEvent(
                event.eventId(),
                KafkaTopics.SUPPORT_ADMIN_REPLY,
                String.valueOf(event.userId()),
                NotificationType.SYSTEM,
                "Nouvelle réponse du support",
                "Un conseiller a répondu à votre demande SAV #" + event.ticketId() + subjectPart + "."
        );
    }
}