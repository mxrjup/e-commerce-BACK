package com.ecommerce.support.dto;

import com.ecommerce.support.entity.TicketCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTicketRequest(
        @NotBlank(message = "Le sujet est obligatoire")
        @Size(max = 200, message = "Le sujet ne doit pas dépasser 200 caractères")
        String subject,

        @NotBlank(message = "Le message initial est obligatoire")
        @Size(max = 5000, message = "Le message ne doit pas dépasser 5000 caractères")
        String message,

        TicketCategory category,
        Long orderId,
        @Size(max = 50, message = "Le numéro de commande ne doit pas dépasser 50 caractères")
        String orderNumber
) {
}