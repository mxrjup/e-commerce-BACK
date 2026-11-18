package com.ecommerce.notifications.service;

import com.ecommerce.common.event.BackInStockEvent;
import com.ecommerce.common.event.PriceDroppedEvent;
import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.kafka.KafkaTopics;
import com.ecommerce.notifications.dto.CatalogAlertResponseDto;
import com.ecommerce.notifications.dto.CreateCatalogAlertRequestDto;
import com.ecommerce.notifications.dto.UpdateCatalogAlertRequestDto;
import com.ecommerce.notifications.entity.CatalogAlertSubscription;
import com.ecommerce.notifications.entity.CatalogAlertType;
import com.ecommerce.notifications.entity.Notification;
import com.ecommerce.notifications.entity.NotificationType;
import com.ecommerce.notifications.entity.ProcessedKafkaEvent;
import com.ecommerce.notifications.repository.CatalogAlertSubscriptionRepository;
import com.ecommerce.notifications.repository.NotificationRepository;
import com.ecommerce.notifications.repository.ProcessedKafkaEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogAlertService {

    private final CatalogAlertSubscriptionRepository alertRepository;
    private final NotificationRepository notificationRepository;
    private final ProcessedKafkaEventRepository processedKafkaEventRepository;
    private final PushNotificationService pushNotificationService;

    @Transactional
    public CatalogAlertResponseDto createAlert(String recipientId, CreateCatalogAlertRequestDto request) {
        validateTargetPriceForType(request.getAlertType(), request.getTargetPriceExclTax() != null);

        CatalogAlertSubscription existing = alertRepository
                .findByRecipientIdAndVariantIdAndAlertType(recipientId, request.getVariantId(), request.getAlertType())
                .orElse(null);

        if (existing != null) {
            if (existing.isActive()) {
                throw new ConflictException("An active alert already exists for this variant and alert type");
            }
            existing.setProductId(request.getProductId() != null ? request.getProductId() : existing.getProductId());
            existing.setTargetPriceExclTax(request.getTargetPriceExclTax());
            existing.setActive(true);
            existing.setTriggeredAt(null);
            return mapToDto(alertRepository.save(existing));
        }

        CatalogAlertSubscription created = CatalogAlertSubscription.builder()
                .recipientId(recipientId)
                .productId(request.getProductId())
                .variantId(request.getVariantId())
                .alertType(request.getAlertType())
                .targetPriceExclTax(request.getTargetPriceExclTax())
                .active(true)
                .build();

        return mapToDto(alertRepository.save(created));
    }

    @Transactional(readOnly = true)
    public List<CatalogAlertResponseDto> getUserAlerts(String recipientId, boolean activeOnly) {
        List<CatalogAlertSubscription> alerts = activeOnly
                ? alertRepository.findByRecipientIdAndActiveTrueOrderByCreatedAtDesc(recipientId)
                : alertRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId);

        return alerts.stream().map(this::mapToDto).toList();
    }

    @Transactional(readOnly = true)
    public CatalogAlertResponseDto getUserAlert(Long alertId, String recipientId) {
        return mapToDto(findOwnedAlert(alertId, recipientId));
    }

    @Transactional
    public CatalogAlertResponseDto updateAlert(Long alertId, String recipientId, UpdateCatalogAlertRequestDto request) {
        CatalogAlertSubscription alert = findOwnedAlert(alertId, recipientId);

        if (request.getTargetPriceExclTax() != null) {
            validateTargetPriceForType(alert.getAlertType(), true);
            alert.setTargetPriceExclTax(request.getTargetPriceExclTax());
        }
        if (request.getActive() != null) {
            alert.setActive(request.getActive());
            if (request.getActive()) {
                alert.setTriggeredAt(null);
            }
        }

        return mapToDto(alertRepository.save(alert));
    }

    @Transactional
    public void deleteAlert(Long alertId, String recipientId) {
        CatalogAlertSubscription alert = findOwnedAlert(alertId, recipientId);
        alertRepository.delete(alert);
    }

    @Transactional
    public int handlePriceDropped(PriceDroppedEvent event) {
        if (processedKafkaEventRepository.existsById(event.eventId())) {
            log.debug("PriceDroppedEvent déjà traité (eventId={}), ignoré.", event.eventId());
            return 0;
        }

        List<CatalogAlertSubscription> subscriptions = alertRepository
                .findByVariantIdAndAlertTypeAndActiveTrue(event.variantId(), CatalogAlertType.PRICE_DROP);

        int notifiedCount = 0;
        Instant now = Instant.now();

        for (CatalogAlertSubscription subscription : subscriptions) {
            if (subscription.getTargetPriceExclTax() != null
                    && event.newUnitPriceExclTax().compareTo(subscription.getTargetPriceExclTax()) > 0) {
                continue;
            }

            String title = "Baisse de prix sur un produit suivi";
            String message = "Le prix d'un produit que vous suivez est passé de "
                    + event.previousUnitPriceExclTax() + " € HT à " + event.newUnitPriceExclTax() + " € HT.";

            Notification saved = notificationRepository.save(
                    Notification.builder()
                            .recipientId(subscription.getRecipientId())
                            .type(NotificationType.VARIANT_ALERT)
                            .title(title)
                            .message(message)
                            .read(false)
                            .build()
            );

            subscription.setActive(false);
            subscription.setTriggeredAt(now);
            alertRepository.save(subscription);

            pushNotificationService.sendPushToUser(
                    subscription.getRecipientId(),
                    title,
                    message,
                    Map.of(
                            "notificationId", saved.getId(),
                            "type", NotificationType.VARIANT_ALERT.name(),
                            "productId", event.productId(),
                            "variantId", event.variantId()
                    )
            );
            notifiedCount++;
        }

        processedKafkaEventRepository.save(
                ProcessedKafkaEvent.builder()
                        .eventId(event.eventId())
                        .topic(KafkaTopics.CATALOG_EVENTS)
                        .processedAt(now)
                        .build()
        );

        return notifiedCount;
    }

    @Transactional
    public int handleBackInStock(BackInStockEvent event) {
        if (processedKafkaEventRepository.existsById(event.eventId())) {
            log.debug("BackInStockEvent déjà traité (eventId={}), ignoré.", event.eventId());
            return 0;
        }

        List<CatalogAlertSubscription> subscriptions = alertRepository
                .findByVariantIdAndAlertTypeAndActiveTrue(event.variantId(), CatalogAlertType.BACK_IN_STOCK);

        int notifiedCount = 0;
        Instant now = Instant.now();

        for (CatalogAlertSubscription subscription : subscriptions) {
            String title = "Produit de nouveau en stock";
            String message = "Un produit que vous suivez est de nouveau disponible en stock.";

            Notification saved = notificationRepository.save(
                    Notification.builder()
                            .recipientId(subscription.getRecipientId())
                            .type(NotificationType.VARIANT_ALERT)
                            .title(title)
                            .message(message)
                            .read(false)
                            .build()
            );

            subscription.setActive(false);
            subscription.setTriggeredAt(now);
            alertRepository.save(subscription);

            pushNotificationService.sendPushToUser(
                    subscription.getRecipientId(),
                    title,
                    message,
                    Map.of(
                            "notificationId", saved.getId(),
                            "type", NotificationType.VARIANT_ALERT.name(),
                            "productId", event.productId(),
                            "variantId", event.variantId()
                    )
            );
            notifiedCount++;
        }

        processedKafkaEventRepository.save(
                ProcessedKafkaEvent.builder()
                        .eventId(event.eventId())
                        .topic(KafkaTopics.CATALOG_EVENTS)
                        .processedAt(now)
                        .build()
        );

        return notifiedCount;
    }

    private CatalogAlertSubscription findOwnedAlert(Long alertId, String recipientId) {
        return alertRepository.findByIdAndRecipientId(alertId, recipientId)
                .orElseThrow(() -> new ResourceNotFoundException("Catalog alert not found: " + alertId));
    }

    private static void validateTargetPriceForType(CatalogAlertType type, boolean hasTargetPrice) {
        if (hasTargetPrice && type != CatalogAlertType.PRICE_DROP) {
            throw new BadRequestException("targetPriceExclTax is only supported for PRICE_DROP alerts");
        }
    }

    private void markEventProcessed(java.util.UUID eventId) {
        if (eventId != null && !processedKafkaEventRepository.existsById(eventId)) {
            processedKafkaEventRepository.save(ProcessedKafkaEvent.builder()
                    .eventId(eventId)
                    .topic(KafkaTopics.CATALOG_EVENTS)
                    .build());
        }
    }

    private CatalogAlertResponseDto mapToDto(CatalogAlertSubscription alert) {
        return CatalogAlertResponseDto.builder()
                .id(alert.getId())
                .productId(alert.getProductId())
                .variantId(alert.getVariantId())
                .alertType(alert.getAlertType())
                .targetPriceExclTax(alert.getTargetPriceExclTax())
                .active(alert.isActive())
                .triggeredAt(alert.getTriggeredAt())
                .createdAt(alert.getCreatedAt())
                .build();
    }
}