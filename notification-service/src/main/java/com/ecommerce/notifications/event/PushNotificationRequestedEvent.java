package com.ecommerce.notifications.event;

import java.util.Map;

public record PushNotificationRequestedEvent(
        String recipientId,
        String title,
        String body,
        Map<String, Object> data
) {
}