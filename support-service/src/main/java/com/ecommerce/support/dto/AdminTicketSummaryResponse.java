package com.ecommerce.support.dto;

import com.ecommerce.support.entity.SupportTicket;
import com.ecommerce.support.entity.TicketCategory;
import com.ecommerce.support.entity.TicketStatus;

import java.time.Instant;

public record AdminTicketSummaryResponse(
        Long id,
        Long userId,
        String userSubject,
        String subject,
        TicketCategory category,
        TicketStatus status,
        Long orderId,
        String orderNumber,
        int messageCount,
        Instant createdAt,
        Instant updatedAt
) {
    public static AdminTicketSummaryResponse from(SupportTicket ticket) {
        return new AdminTicketSummaryResponse(
                ticket.getId(),
                ticket.getUserId(),
                ticket.getUserSubject(),
                ticket.getSubject(),
                ticket.getCategory(),
                ticket.getStatus(),
                ticket.getOrderId(),
                ticket.getOrderNumber(),
                ticket.getMessages() != null ? ticket.getMessages().size() : 0,
                ticket.getCreatedAt(),
                ticket.getUpdatedAt()
        );
    }
}