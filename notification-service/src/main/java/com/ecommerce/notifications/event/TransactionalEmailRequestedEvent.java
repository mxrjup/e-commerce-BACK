package com.ecommerce.notifications.event;

import java.math.BigDecimal;

public record TransactionalEmailRequestedEvent(
        EmailKind kind,
        String toEmail,
        String firstName,
        String orderNumber,
        BigDecimal totalInclTax,
        Long ticketId,
        String ticketSubject
) {
    public enum EmailKind {
        ORDER_CONFIRMATION,
        ORDER_SHIPPED,
        SUPPORT_ADMIN_REPLY
    }

    public static TransactionalEmailRequestedEvent orderConfirmation(
            String toEmail, String firstName, String orderNumber, BigDecimal totalInclTax) {
        return new TransactionalEmailRequestedEvent(
                EmailKind.ORDER_CONFIRMATION, toEmail, firstName, orderNumber, totalInclTax, null, null);
    }

    public static TransactionalEmailRequestedEvent orderShipped(
            String toEmail, String firstName, String orderNumber) {
        return new TransactionalEmailRequestedEvent(
                EmailKind.ORDER_SHIPPED, toEmail, firstName, orderNumber, null, null, null);
    }

    public static TransactionalEmailRequestedEvent supportAdminReply(
            String toEmail, String firstName, Long ticketId, String ticketSubject) {
        return new TransactionalEmailRequestedEvent(
                EmailKind.SUPPORT_ADMIN_REPLY, toEmail, firstName, null, null, ticketId, ticketSubject);
    }
}