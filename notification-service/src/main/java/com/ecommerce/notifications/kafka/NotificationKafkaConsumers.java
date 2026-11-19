package com.ecommerce.notifications.kafka;

import com.ecommerce.common.event.BackInStockEvent;
import com.ecommerce.common.event.CatalogEvent;
import com.ecommerce.common.event.OrderPaidEvent;
import com.ecommerce.common.event.OrderStatusChangedEvent;
import com.ecommerce.common.event.PriceDroppedEvent;
import com.ecommerce.common.event.SupportAdminReplyEvent;
import com.ecommerce.common.kafka.KafkaTopics;
import com.ecommerce.notifications.client.UserContactResolver;
import com.ecommerce.notifications.client.UserContactResolver.UserContact;
import com.ecommerce.notifications.entity.NotificationType;
import com.ecommerce.notifications.event.TransactionalEmailRequestedEvent;
import com.ecommerce.notifications.service.CatalogAlertService;
import com.ecommerce.notifications.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationKafkaConsumers {

    private final NotificationService notificationService;
    private final CatalogAlertService catalogAlertService;
    private final UserContactResolver userContactResolver;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    @KafkaListener(topics = "${app.kafka.topics.order-paid:" + KafkaTopics.ORDER_PAID + "}")
    public void onOrderPaid(OrderPaidEvent event) {
        Optional<UserContact> contactOpt = userContactResolver.resolveUserContact(event.userId());
        if (contactOpt.isEmpty() || !contactOpt.get().hasSubject()) {
            log.info("Commande ignorée pour les notifications (orderId={}, orderNumber={}, userId={})",
                    event.orderId(), event.orderNumber(), event.userId());
            notificationService.markEventProcessed(event.eventId(), KafkaTopics.ORDER_PAID);
            return;
        }

        UserContact contact = contactOpt.get();
        String amountText = event.totalInclTax() != null
                ? " d'un montant de " + NumberFormat.getCurrencyInstance(Locale.FRANCE).format(event.totalInclTax())
                : "";

        boolean created = notificationService.createFromEvent(
                event.eventId(),
                KafkaTopics.ORDER_PAID,
                contact.subject(),
                NotificationType.ORDER_UPDATE,
                "Commande confirmée",
                "Votre commande " + event.orderNumber() + amountText + " a bien été payée et est en cours de préparation."
        );

        if (created && contact.hasEmail()) {
            eventPublisher.publishEvent(TransactionalEmailRequestedEvent.orderConfirmation(
                    contact.email(),
                    contact.firstName(),
                    event.orderNumber(),
                    event.totalInclTax()
            ));
        }
    }

    @Transactional
    @KafkaListener(topics = "${app.kafka.topics.order-status-changed:" + KafkaTopics.ORDER_STATUS_CHANGED + "}")
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        if (!"SHIPPED".equals(event.newStatus())) {
            notificationService.markEventProcessed(event.eventId(), KafkaTopics.ORDER_STATUS_CHANGED);
            return;
        }

        Optional<UserContact> contactOpt = userContactResolver.resolveUserContact(event.userId());
        if (contactOpt.isEmpty() || !contactOpt.get().hasSubject()) {
            log.info("Expédition ignorée pour les notifications (orderId={}, orderNumber={}, userId={})",
                    event.orderId(), event.orderNumber(), event.userId());
            notificationService.markEventProcessed(event.eventId(), KafkaTopics.ORDER_STATUS_CHANGED);
            return;
        }

        UserContact contact = contactOpt.get();
        boolean created = notificationService.createFromEvent(
                event.eventId(),
                KafkaTopics.ORDER_STATUS_CHANGED,
                contact.subject(),
                NotificationType.ORDER_UPDATE,
                "Commande expédiée",
                "Votre commande " + event.orderNumber() + " a été expédiée."
        );

        if (created && contact.hasEmail()) {
            eventPublisher.publishEvent(TransactionalEmailRequestedEvent.orderShipped(
                    contact.email(),
                    contact.firstName(),
                    event.orderNumber()
            ));
        }
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

    @Transactional
    @KafkaListener(topics = "${app.kafka.topics.support-admin-reply:" + KafkaTopics.SUPPORT_ADMIN_REPLY + "}")
    public void onSupportAdminReply(SupportAdminReplyEvent event) {
        Optional<UserContact> contactOpt = userContactResolver.resolveUserContact(event.userId());
        if (contactOpt.isEmpty() || !contactOpt.get().hasSubject()) {
            log.warn("Réponse SAV ignorée car aucun contact résolu pour userId={} (ticketId={})",
                    event.userId(), event.ticketId());
            notificationService.markEventProcessed(event.eventId(), KafkaTopics.SUPPORT_ADMIN_REPLY);
            return;
        }

        UserContact contact = contactOpt.get();
        String subjectPart = (event.ticketSubject() != null && !event.ticketSubject().isBlank())
                ? " (« " + event.ticketSubject() + " »)"
                : "";

        boolean created = notificationService.createFromEvent(
                event.eventId(),
                KafkaTopics.SUPPORT_ADMIN_REPLY,
                contact.subject(),
                NotificationType.SYSTEM,
                "Nouvelle réponse du support",
                "Un conseiller a répondu à votre demande SAV #" + event.ticketId() + subjectPart + "."
        );

        if (created && contact.hasEmail()) {
            eventPublisher.publishEvent(TransactionalEmailRequestedEvent.supportAdminReply(
                    contact.email(),
                    contact.firstName(),
                    event.ticketId(),
                    event.ticketSubject()
            ));
        }
    }
}