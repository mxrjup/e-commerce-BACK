package com.ecommerce.notifications.listener;

import com.ecommerce.notifications.event.TransactionalEmailRequestedEvent;
import com.ecommerce.notifications.service.TransactionalEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionalEmailEventListener {

    private final TransactionalEmailService transactionalEmailService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTransactionalEmailRequested(TransactionalEmailRequestedEvent event) {
        try {
            switch (event.kind()) {
                case ORDER_CONFIRMATION -> transactionalEmailService.sendOrderConfirmation(
                        event.toEmail(), event.firstName(), event.orderNumber(), event.totalInclTax());
                case ORDER_SHIPPED -> transactionalEmailService.sendOrderShipped(
                        event.toEmail(), event.firstName(), event.orderNumber());
                case SUPPORT_ADMIN_REPLY -> transactionalEmailService.sendSupportAdminReply(
                        event.toEmail(), event.firstName(), event.ticketId(), event.ticketSubject());
            }
        } catch (Exception e) {
            log.error("Erreur asynchrone lors de l'envoi d'email ({}) à {} : {}",
                    event.kind(), event.toEmail(), e.getMessage());
        }
    }
}