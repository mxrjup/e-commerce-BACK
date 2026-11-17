package com.ecommerce.notifications.kafka;

import com.ecommerce.common.event.BackInStockEvent;
import com.ecommerce.common.event.CatalogEvent;
import com.ecommerce.common.event.OrderPaidEvent;
import com.ecommerce.common.event.PriceDroppedEvent;
import com.ecommerce.common.event.SupportAdminReplyEvent;
import com.ecommerce.common.kafka.KafkaTopics;
import com.ecommerce.notifications.entity.NotificationType;
import com.ecommerce.notifications.service.CatalogAlertService;
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
    private final CatalogAlertService catalogAlertService;

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