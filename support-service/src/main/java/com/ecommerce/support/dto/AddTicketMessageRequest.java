package com.ecommerce.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddTicketMessageRequest(
        @NotBlank(message = "Le contenu du message est obligatoire")
        @Size(max = 5000, message = "Le message ne doit pas dépasser 5000 caractères")
        String content
) {
}