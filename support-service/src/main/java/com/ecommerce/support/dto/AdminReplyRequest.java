package com.ecommerce.support.dto;

import com.ecommerce.support.entity.TicketStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminReplyRequest(
        @NotBlank(message = "Le contenu de la réponse est obligatoire")
        @Size(max = 5000, message = "La réponse ne doit pas dépasser 5000 caractères")
        String content,
        TicketStatus status
) {
}