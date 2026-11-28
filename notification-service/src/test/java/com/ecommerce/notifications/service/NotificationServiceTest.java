package com.ecommerce.notifications.service;

import com.ecommerce.notifications.dto.NotificationResponseDto;
import com.ecommerce.notifications.entity.Notification;
import com.ecommerce.notifications.entity.NotificationType;
import com.ecommerce.notifications.entity.VariantAlert;
import com.ecommerce.notifications.entity.VariantAlertType;
import com.ecommerce.notifications.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import com.ecommerce.notifications.repository.ProcessedKafkaEventRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private ProcessedKafkaEventRepository processedKafkaEventRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    @DisplayName("Should return notifications with variant alert summary for recipient")
    void getUserNotifications_ShouldReturnMappedList() {
        VariantAlert alert = VariantAlert.builder()
                .id(10L)
                .variantId(42L)
                .sku("LAPTOP-PRO-16")
                .productName("LogBook Pro 16")
                .alertType(VariantAlertType.BACK_IN_STOCK)
                .currentStock(12)
                .threshold(5)
                .resolved(false)
                .build();

        Notification notif = Notification.builder()
                .id(1L)
                .recipientId("user-sub-123")
                .type(NotificationType.VARIANT_ALERT)
                .title("Produit de nouveau disponible")
                .message("LogBook Pro 16 est de nouveau en stock.")
                .variantAlert(alert)
                .read(false)
                .createdAt(Instant.now())
                .build();

        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc("user-sub-123"))
                .thenReturn(List.of(notif));

        List<NotificationResponseDto> result = notificationService.getUserNotifications("user-sub-123", false);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isRead()).isFalse();
        assertThat(result.get(0).getVariantAlert().getSku()).isEqualTo("LAPTOP-PRO-16");
    }

    @Test
    @DisplayName("Should mark notification as read and set readAt timestamp")
    void markAsRead_ShouldUpdateState_WhenOwnedByUser() {
        Notification notif = Notification.builder()
                .id(1L)
                .recipientId("user-sub-123")
                .type(NotificationType.ORDER_UPDATE)
                .title("Commande expédiée")
                .message("Votre colis est en route.")
                .read(false)
                .build();

        when(notificationRepository.findByIdAndRecipientId(1L, "user-sub-123"))
                .thenReturn(Optional.of(notif));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        NotificationResponseDto result = notificationService.markAsRead(1L, "user-sub-123");

        assertThat(result.isRead()).isTrue();
        assertThat(result.getReadAt()).isNotNull();
        verify(notificationRepository).save(notif);
    }

    @Test
    @DisplayName("Should throw EntityNotFoundException when notification belongs to another user")
    void markAsRead_ShouldThrow_WhenNotFoundOrWrongOwner() {
        when(notificationRepository.findByIdAndRecipientId(99L, "user-sub-123"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(99L, "user-sub-123"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verify(notificationRepository, never()).save(any());
    }
}