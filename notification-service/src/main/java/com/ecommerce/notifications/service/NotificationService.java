package com.ecommerce.notifications.service;

import com.ecommerce.notifications.dto.NotificationResponseDto;
import com.ecommerce.notifications.dto.VariantAlertSummaryDto;
import com.ecommerce.notifications.entity.Notification;
import com.ecommerce.notifications.entity.NotificationType;
import com.ecommerce.notifications.entity.ProcessedKafkaEvent;
import com.ecommerce.notifications.entity.VariantAlert;
import com.ecommerce.notifications.repository.NotificationRepository;
import com.ecommerce.notifications.repository.ProcessedKafkaEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.context.ApplicationEventPublisher;
import com.ecommerce.notifications.event.PushNotificationRequestedEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final ProcessedKafkaEventRepository processedKafkaEventRepository;
    private final ApplicationEventPublisher eventPublisher;     

    @Transactional(readOnly = true)
    public List<NotificationResponseDto> getUserNotifications(String recipientId, boolean unreadOnly) {
        List<Notification> notifications = unreadOnly
                ? notificationRepository.findByRecipientIdAndReadFalseOrderByCreatedAtDesc(recipientId)
                : notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId);

        return notifications.stream()
                .map(this::mapToDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(String recipientId) {
        return notificationRepository.countByRecipientIdAndReadFalse(recipientId);
    }

    @Transactional
    public NotificationResponseDto markAsRead(Long notificationId, String recipientId) {
        Notification notification = notificationRepository.findByIdAndRecipientId(notificationId, recipientId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Notification not found: " + notificationId));

        notification.markAsRead();
        return mapToDto(notificationRepository.save(notification));
    }

    @Transactional
    public void markAllAsRead(String recipientId) {
        notificationRepository.markAllAsReadByRecipientId(recipientId, Instant.now());
    }

    /**
     * Crée et persiste une notification issue d'un événement Kafka de manière idempotente.
     * Si l'eventId a déjà été traité, l'appel est ignoré sans créer de doublon.
     */
    @Transactional
    public void createFromEvent(
            UUID eventId,
            String topic,
            String recipientId,
            NotificationType type,
            String title,
            String message) {
        if (processedKafkaEventRepository.existsById(eventId)) {
            log.debug("Événement Kafka déjà traité (eventId={}, topic={}), ignoré.", eventId, topic);
            return;
        }

        Notification saved = notificationRepository.save(
                Notification.builder()
                        .recipientId(recipientId)
                        .type(type)
                        .title(title)
                        .message(message)
                        .read(false)
                        .build()
        );

        processedKafkaEventRepository.save(
                ProcessedKafkaEvent.builder()
                        .eventId(eventId)
                        .topic(topic)
                        .processedAt(Instant.now())
                        .build()
        );

        eventPublisher.publishEvent(new PushNotificationRequestedEvent(
                recipientId,
                title,
                message,
                Map.of(
                        "notificationId", saved.getId(),
                        "type", type.name()
                )
        ));
    }

    /**
     * Marque un événement Kafka comme traité sans créer de notification utilisateur
     * (ex. commande invité sans userId ou événement catalogue sans abonné actif).
     */
    @Transactional
    public boolean markEventProcessed(UUID eventId, String topic) {
        if (eventId == null || processedKafkaEventRepository.existsById(eventId)) {
            return false;
        }
        processedKafkaEventRepository.save(ProcessedKafkaEvent.builder()
                .eventId(eventId)
                .topic(topic)
                .build());
        return true;
    }

    private NotificationResponseDto mapToDto(Notification notification) {
        VariantAlert alert = notification.getVariantAlert();
        VariantAlertSummaryDto alertDto = alert == null ? null : VariantAlertSummaryDto.builder()
                .id(alert.getId())
                .variantId(alert.getVariantId())
                .sku(alert.getSku())
                .productName(alert.getProductName())
                .alertType(alert.getAlertType())
                .currentStock(alert.getCurrentStock())
                .threshold(alert.getThreshold())
                .resolved(alert.isResolved())
                .build();

        return NotificationResponseDto.builder()
                .id(notification.getId())
                .type(notification.getType())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .read(notification.isRead())
                .readAt(notification.getReadAt())
                .createdAt(notification.getCreatedAt())
                .variantAlert(alertDto)
                .build();
    }
}