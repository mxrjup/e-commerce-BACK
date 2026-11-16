package com.ecommerce.notifications.controller;

import com.ecommerce.notifications.config.SecurityConfig;
import com.ecommerce.notifications.dto.NotificationResponseDto;
import com.ecommerce.notifications.entity.NotificationType;
import com.ecommerce.notifications.exception.GlobalExceptionHandler;
import com.ecommerce.notifications.service.NotificationService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("GET /api/v1/notifications should return 401 without JWT")
    void listNotifications_WithoutJwt_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/notifications should return user notifications")
    void listNotifications_WithJwt_ShouldReturnOk() throws Exception {
        NotificationResponseDto dto = NotificationResponseDto.builder()
                .id(1L)
                .type(NotificationType.SYSTEM)
                .title("Bienvenue")
                .message("Votre compte est actif")
                .read(false)
                .createdAt(Instant.now())
                .build();

        when(notificationService.getUserNotifications("user-123", false))
                .thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/notifications")
                        .with(jwt().jwt(j -> j.subject("user-123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value("Bienvenue"))
                .andExpect(jsonPath("$[0].read").value(false));
    }

    @Test
    @DisplayName("PATCH /api/v1/notifications/{id}/read should return 404 when not owned")
    void markAsRead_WhenNotFound_ShouldReturnNotFound() throws Exception {
        when(notificationService.markAsRead(999L, "user-123"))
                .thenThrow(new EntityNotFoundException("Notification not found: 999"));

        mockMvc.perform(patch("/api/v1/notifications/999/read")
                        .with(jwt().jwt(j -> j.subject("user-123"))))
                .andExpect(status().isNotFound());
    }
}