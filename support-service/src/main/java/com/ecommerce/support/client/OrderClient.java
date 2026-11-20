package com.ecommerce.support.client;

import com.ecommerce.common.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
public class OrderClient {

    private static final String HEADER_USER_ID = "X-User-Id";

    private final RestClient restClient;

    public OrderClient(
            RestClient.Builder restClientBuilder,
            @Value("${services.order-service.uri:http://localhost:8084}") String orderServiceUri) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = restClientBuilder
                .baseUrl(orderServiceUri)
                .requestFactory(requestFactory)
                .build();
    }

    public OrderSummary getCustomerOrderById(Long userId, Long orderId) {
        try {
            OrderSummary response = restClient.get()
                    .uri("/api/orders/{orderId}", orderId)
                    .header(HEADER_USER_ID, String.valueOf(userId))
                    .retrieve()
                    .body(OrderSummary.class);
            if (response == null) {
                throw new ResourceNotFoundException("Commande introuvable : " + orderId);
            }
            return response;
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResourceNotFoundException("Commande introuvable : " + orderId);
        } catch (RestClientException e) {
            log.error("Erreur lors de l'appel à order-service pour orderId={} : {}", orderId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Service Commande indisponible");
        }
    }

    public OrderSummary getCustomerOrderByNumber(Long userId, String orderNumber) {
        try {
            List<OrderSummary> orders = restClient.get()
                    .uri("/api/orders/history")
                    .header(HEADER_USER_ID, String.valueOf(userId))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (orders == null) {
                throw new ResourceNotFoundException("Commande introuvable : " + orderNumber);
            }
            return orders.stream()
                    .filter(order -> order.orderNumber() != null && order.orderNumber().equalsIgnoreCase(orderNumber))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Commande introuvable : " + orderNumber));
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("Erreur lors de l'appel à order-service pour orderNumber={} : {}", orderNumber, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Service Commande indisponible");
        }
    }

    public record OrderSummary(
            Long id,
            String orderNumber,
            String status,
            Instant createdAt
    ) {
    }
}