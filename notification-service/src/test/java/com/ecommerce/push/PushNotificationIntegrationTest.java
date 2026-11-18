package com.ecommerce.notifications.push;

import com.ecommerce.notifications.client.ExpoPushClient;
import com.ecommerce.notifications.client.ExpoPushClient.ExpoPushErrorDetails;
import com.ecommerce.notifications.client.ExpoPushClient.ExpoPushTicket;
import com.ecommerce.notifications.dto.RegisterPushTokenRequestDto;
import com.ecommerce.notifications.entity.PushDeviceToken;
import com.ecommerce.notifications.repository.PushDeviceTokenRepository;
import com.ecommerce.notifications.service.PushNotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("local-h2")
class PushNotificationIntegrationTest {

    private static final String USER_SUB = "7f8b1c2d-4242-4242-8888-abcdef123456";
    private static final String VALID_TOKEN = "ExponentPushToken[xxxxxxxxxxxxxxxxxxxxxx]";
    private static final String STALE_TOKEN = "ExponentPushToken[yyyyyyyyyyyyyyyyyyyyyy]";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PushDeviceTokenRepository tokenRepository;

    @Autowired
    private PushNotificationService pushNotificationService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private ExpoPushClient expoPushClient;

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAll();
    }

    @Test
    void shouldRegisterAndListExpoPushToken() throws Exception {
        RegisterPushTokenRequestDto request = RegisterPushTokenRequestDto.builder()
                .expoToken(VALID_TOKEN)
                .platform("android")
                .build();

        mockMvc.perform(post("/api/notifications/push-tokens")
                        .with(jwt().jwt(token -> token.subject(USER_SUB)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expoToken").value(VALID_TOKEN))
                .andExpect(jsonPath("$.platform").value("android"))
                .andExpect(jsonPath("$.active").value(true));

        assertThat(tokenRepository.findByRecipientIdAndActiveTrue(USER_SUB)).hasSize(1);

        mockMvc.perform(get("/api/notifications/push-tokens")
                        .with(jwt().jwt(token -> token.subject(USER_SUB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].expoToken").value(VALID_TOKEN));
    }

    @Test
    void shouldRejectInvalidExpoPushTokenWithBadRequest() throws Exception {
        RegisterPushTokenRequestDto invalidRequest = RegisterPushTokenRequestDto.builder()
                .expoToken("invalid-fcm-token-format")
                .platform("ios")
                .build();

        mockMvc.perform(post("/api/notifications/push-tokens")
                        .with(jwt().jwt(token -> token.subject(USER_SUB)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        assertThat(tokenRepository.findAll()).isEmpty();
    }

    @Test
    void shouldDeactivateTokenWhenExpoReturnsDeviceNotRegistered() {
        tokenRepository.save(PushDeviceToken.builder()
                .recipientId(USER_SUB)
                .expoToken(VALID_TOKEN)
                .platform("ios")
                .active(true)
                .build());

        tokenRepository.save(PushDeviceToken.builder()
                .recipientId(USER_SUB)
                .expoToken(STALE_TOKEN)
                .platform("android")
                .active(true)
                .build());

        when(expoPushClient.sendBatch(anyList())).thenReturn(List.of(
                new ExpoPushTicket("ok", "ticket-1", null, null),
                new ExpoPushTicket("error", null, "Device not registered", new ExpoPushErrorDetails("DeviceNotRegistered"))
        ));

        pushNotificationService.sendPushToUser(
                USER_SUB,
                "Commande confirmée",
                "Votre commande CMD-2026-000101 a bien été payée.",
                Map.of("notificationId", 1L, "type", "ORDER_UPDATE")
        );

        PushDeviceToken activeDevice = tokenRepository.findByExpoToken(VALID_TOKEN).orElseThrow();
        assertThat(activeDevice.isActive()).isTrue();
        assertThat(activeDevice.getInvalidatedAt()).isNull();

        PushDeviceToken invalidatedDevice = tokenRepository.findByExpoToken(STALE_TOKEN).orElseThrow();
        assertThat(invalidatedDevice.isActive()).isFalse();
        assertThat(invalidatedDevice.getInvalidatedAt()).isNotNull();

        // Un second envoi ne sollicite plus que le token resté actif
        when(expoPushClient.sendBatch(anyList())).thenReturn(List.of(
                new ExpoPushTicket("ok", "ticket-2", null, null)
        ));

        pushNotificationService.sendPushToUser(
                USER_SUB,
                "Nouvelle réponse du support",
                "Un conseiller a répondu à votre demande.",
                Map.of("notificationId", 2L, "type", "SYSTEM")
        );

        verify(expoPushClient, times(2)).sendBatch(anyList());
        assertThat(tokenRepository.findByRecipientIdAndActiveTrue(USER_SUB))
                .extracting(PushDeviceToken::getExpoToken)
                .containsExactly(VALID_TOKEN);
    }

    @Test
    void shouldUnregisterPushToken() throws Exception {
        tokenRepository.save(PushDeviceToken.builder()
                .recipientId(USER_SUB)
                .expoToken(VALID_TOKEN)
                .platform("ios")
                .active(true)
                .build());

        mockMvc.perform(delete("/api/notifications/push-tokens")
                        .with(jwt().jwt(token -> token.subject(USER_SUB)))
                        .param("expoToken", VALID_TOKEN))
                .andExpect(status().isNoContent());

        assertThat(tokenRepository.findByExpoToken(VALID_TOKEN)).isEmpty();
    }
}