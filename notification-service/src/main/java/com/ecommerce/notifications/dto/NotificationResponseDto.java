package com.ecommerce.notifications.dto;

import com.ecommerce.notifications.entity.NotificationType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class NotificationResponseDto {
    private Long id;
    private NotificationType type;
    private String title;
    private String message;
    private boolean read;
    private Instant readAt;
    private Instant createdAt;
    private VariantAlertSummaryDto variantAlert;
}