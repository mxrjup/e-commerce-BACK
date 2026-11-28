package com.ecommerce.support.dto;

import com.ecommerce.support.entity.SupportTicket;
import com.ecommerce.support.entity.TicketCategory;
import com.ecommerce.support.entity.TicketStatus;

import java.time.Instant;
import java.util.List;

public record TicketDetailResponse(
        Long id,
        String subject,
        TicketCategory category,
        TicketStatus status,
        Long orderId,
        String orderNumber,
        Instant createdAt,
        Instant updatedAt,
        List<TicketMessageResponse> messages
) {
    public static TicketDetailResponse from(SupportTicket ticket) {
        List<TicketMessageResponse> items = ticket.getMessages() == null
                ? List.of()
                : ticket.getMessages().stream().map(TicketMessageResponse::from).toList();
        return new TicketDetailResponse(
                ticket.getId(),
                ticket.getSubject(),
                ticket.getCategory(),
                ticket.getStatus(),
                ticket.getOrderId(),
                ticket.getOrderNumber(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                items
        );
    }
}