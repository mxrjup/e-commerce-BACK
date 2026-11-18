package com.ecommerce.notifications.service;

import com.ecommerce.notifications.client.ExpoPushClient;
import com.ecommerce.notifications.client.ExpoPushClient.ExpoPushMessage;
import com.ecommerce.notifications.client.ExpoPushClient.ExpoPushTicket;
import com.ecommerce.notifications.dto.PushTokenResponseDto;
import com.ecommerce.notifications.dto.RegisterPushTokenRequestDto;
import com.ecommerce.notifications.entity.PushDeviceToken;
import com.ecommerce.notifications.repository.PushDeviceTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationService {

    private final PushDeviceTokenRepository tokenRepository;
    private final ExpoPushClient expoPushClient;

    @Transactional
    public PushTokenResponseDto registerToken(String recipientId, RegisterPushTokenRequestDto request) {
        Instant now = Instant.now();

        PushDeviceToken entity = tokenRepository.findByExpoToken(request.getExpoToken())
                .map(existing -> {
                    existing.setRecipientId(recipientId);
                    existing.setPlatform(request.getPlatform());
                    existing.setActive(true);
                    existing.setInvalidatedAt(null);
                    existing.setLastUsedAt(now);
                    return existing;
                })
                .orElseGet(() -> PushDeviceToken.builder()
                        .recipientId(recipientId)
                        .expoToken(request.getExpoToken())
                        .platform(request.getPlatform())
                        .active(true)
                        .lastUsedAt(now)
                        .build());

        PushDeviceToken saved = tokenRepository.save(entity);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<PushTokenResponseDto> listActiveTokens(String recipientId) {
        return tokenRepository.findByRecipientIdAndActiveTrue(recipientId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public void unregisterToken(String recipientId, String expoToken) {
        tokenRepository.deleteByRecipientIdAndExpoToken(recipientId, expoToken);
    }

    @Transactional
    public void sendPushToUser(String recipientId, String title, String body, Map<String, Object> data) {
        List<PushDeviceToken> activeTokens = tokenRepository.findByRecipientIdAndActiveTrue(recipientId);
        if (activeTokens.isEmpty()) {
            return;
        }

        List<ExpoPushMessage> messages = activeTokens.stream()
                .map(token -> ExpoPushMessage.of(token.getExpoToken(), title, body, data))
                .toList();

        List<ExpoPushTicket> tickets = expoPushClient.sendBatch(messages);
        Instant now = Instant.now();

        for (int i = 0; i < activeTokens.size(); i++) {
            PushDeviceToken deviceToken = activeTokens.get(i);
            if (i < tickets.size()) {
                ExpoPushTicket ticket = tickets.get(i);
                if (ticket.isDeviceNotRegistered()) {
                    log.warn("Token Expo désactivé (DeviceNotRegistered) pour recipientId={}, tokenId={}",
                            recipientId, deviceToken.getId());
                    deviceToken.setActive(false);
                    deviceToken.setInvalidatedAt(now);
                    tokenRepository.save(deviceToken);
                    continue;
                }
                if ("error".equalsIgnoreCase(ticket.status())) {
                    log.warn("Échec d'envoi push Expo pour tokenId={} : {} ({})",
                            deviceToken.getId(), ticket.message(),
                            ticket.details() != null ? ticket.details().error() : "unknown");
                    continue;
                }
            }
            deviceToken.setLastUsedAt(now);
            tokenRepository.save(deviceToken);
        }
    }

    private PushTokenResponseDto toDto(PushDeviceToken token) {
        return PushTokenResponseDto.builder()
                .id(token.getId())
                .expoToken(token.getExpoToken())
                .platform(token.getPlatform())
                .active(token.isActive())
                .updatedAt(token.getUpdatedAt())
                .build();
    }
}