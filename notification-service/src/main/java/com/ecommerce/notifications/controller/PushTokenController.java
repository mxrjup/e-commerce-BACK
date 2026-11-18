package com.ecommerce.notifications.controller;

import com.ecommerce.notifications.dto.PushTokenResponseDto;
import com.ecommerce.notifications.dto.RegisterPushTokenRequestDto;
import com.ecommerce.notifications.service.PushNotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications/push-tokens")
@RequiredArgsConstructor
public class PushTokenController {

    private final PushNotificationService pushNotificationService;

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public PushTokenResponseDto registerToken(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RegisterPushTokenRequestDto request) {
        return pushNotificationService.registerToken(jwt.getSubject(), request);
    }

    @GetMapping
    public List<PushTokenResponseDto> listMyActiveTokens(@AuthenticationPrincipal Jwt jwt) {
        return pushNotificationService.listActiveTokens(jwt.getSubject());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unregisterToken(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam("expoToken") String expoToken) {
        pushNotificationService.unregisterToken(jwt.getSubject(), expoToken);
    }
}