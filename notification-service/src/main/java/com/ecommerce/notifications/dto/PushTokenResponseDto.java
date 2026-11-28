package com.ecommerce.notifications.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PushTokenResponseDto {
    private Long id;
    private String expoToken;
    private String platform;
    private boolean active;
    private Instant updatedAt;
}