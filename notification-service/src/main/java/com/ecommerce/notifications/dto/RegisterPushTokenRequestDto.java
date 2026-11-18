package com.ecommerce.notifications.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterPushTokenRequestDto {

    @NotBlank(message = "Le token Expo est obligatoire")
    @Size(max = 255)
    @Pattern(
            regexp = "^Expo(nent)?PushToken\\[[^\\]]+\\]$",
            message = "Format de token Expo invalide (attendu : ExponentPushToken[...] ou ExpoPushToken[...])"
    )
    private String expoToken;

    @Size(max = 32)
    private String platform;
}