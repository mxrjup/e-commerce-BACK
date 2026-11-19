package com.ecommerce.support.dto;

import com.ecommerce.support.entity.MessageAuthorRole;
import com.ecommerce.support.entity.TicketMessage;

import java.time.Instant;

public record TicketMessageResponse(
        Long id,
        MessageAuthorRole authorRole,
        String content,
        Instant createdAt
) {
    public static TicketMessageResponse from(TicketMessage message) {
        return new TicketMessageResponse(
                message.getId(),
                message.getAuthorRole(),
                message.getContent(),
                message.getCreatedAt()
        );
    }
}