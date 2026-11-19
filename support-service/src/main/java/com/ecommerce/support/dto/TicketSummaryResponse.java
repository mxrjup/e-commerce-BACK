package com.ecommerce.support.dto;

import com.ecommerce.support.entity.SupportTicket;
import com.ecommerce.support.entity.TicketCategory;
import com.ecommerce.support.entity.TicketStatus;

import java.time.Instant;

public record TicketSummaryResponse(
        Long id,
        String subject,
        TicketCategory category,
        TicketStatus status,
        Long orderId,
        String orderNumber,
        int messageCount,
        Instant createdAt,
        Instant updatedAt
) {
    public static TicketSummaryResponse from(SupportTicket ticket) {
        return new TicketSummaryResponse(
                ticket.getId(),
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