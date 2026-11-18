package com.ecommerce.notifications.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ExpoPushClient {

    private final RestClient restClient;
    private final boolean enabled;

    public ExpoPushClient(
            RestClient.Builder restClientBuilder,
            @Value("${expo.push.api-url:https://exp.host/--/api/v2/push/send}") String expoApiUrl,
            @Value("${expo.push.enabled:true}") boolean enabled,
            @Value("${expo.push.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${expo.push.read-timeout-ms:5000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        this.restClient = restClientBuilder
                .baseUrl(expoApiUrl)
                .requestFactory(requestFactory)
                .build();
        this.enabled = enabled;
    }

    public List<ExpoPushTicket> sendBatch(List<ExpoPushMessage> messages) {
        if (!enabled || messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            ExpoPushResponse response = restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(messages)
                    .retrieve()
                    .body(ExpoPushResponse.class);

            if (response == null || response.data() == null) {
                return Collections.emptyList();
            }
            return response.data();
        } catch (Exception e) {
            log.error("Erreur lors de l'appel à l'API Expo Push ({} messages) : {}",
                    messages.size(), e.getMessage());
            return Collections.emptyList();
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ExpoPushMessage(
            String to,
            String title,
            String body,
            String sound,
            Map<String, Object> data
    ) {
        public static ExpoPushMessage of(String to, String title, String body, Map<String, Object> data) {
            return new ExpoPushMessage(to, title, body, "default", data);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExpoPushResponse(List<ExpoPushTicket> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExpoPushTicket(
            String status,
            String id,
            String message,
            ExpoPushErrorDetails details
    ) {
        public boolean isDeviceNotRegistered() {
            return "error".equalsIgnoreCase(status)
                    && details != null
                    && "DeviceNotRegistered".equalsIgnoreCase(details.error());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExpoPushErrorDetails(String error) {
    }
}