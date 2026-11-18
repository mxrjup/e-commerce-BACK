package com.ecommerce.notifications.listener;

import com.ecommerce.notifications.event.PushNotificationRequestedEvent;
import com.ecommerce.notifications.service.PushNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class PushNotificationEventListener {

    private final PushNotificationService pushNotificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPushNotificationRequested(PushNotificationRequestedEvent event) {
        try {
            pushNotificationService.sendPushToUser(
                    event.recipientId(),
                    event.title(),
                    event.body(),
                    event.data()
            );
        } catch (Exception e) {
            log.error("Erreur asynchrone lors de l'envoi push pour recipientId={} : {}",
                    event.recipientId(), e.getMessage());
        }
    }
}
