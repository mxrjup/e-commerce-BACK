package com.ecommerce.notifications.service;

import com.ecommerce.notifications.dto.NotificationResponseDto;
import com.ecommerce.notifications.dto.VariantAlertSummaryDto;
import com.ecommerce.notifications.entity.Notification;
import com.ecommerce.notifications.entity.VariantAlert;
import com.ecommerce.notifications.repository.NotificationRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

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
                .orElseThrow(() -> new EntityNotFoundException("Notification not found: " + notificationId));

        notification.markAsRead();
        return mapToDto(notificationRepository.save(notification));
    }

    @Transactional
    public void markAllAsRead(String recipientId) {
        notificationRepository.markAllAsReadByRecipientId(recipientId, Instant.now());
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