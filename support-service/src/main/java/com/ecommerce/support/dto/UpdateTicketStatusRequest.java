package com.ecommerce.support.dto;

import com.ecommerce.support.entity.TicketStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateTicketStatusRequest(
        @NotNull(message = "Le statut est obligatoire")
        TicketStatus status
) {
}