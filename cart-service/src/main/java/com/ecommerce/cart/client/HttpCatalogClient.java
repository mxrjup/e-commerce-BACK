package com.ecommerce.cart.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Component
public class HttpCatalogClient implements CatalogClient {

    private static final Logger log = LoggerFactory.getLogger(HttpCatalogClient.class);

    private final RestClient restClient;

    public HttpCatalogClient(@Value("${app.catalog-service.url:http://localhost:8081}") String catalogUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(2000);
        requestFactory.setReadTimeout(3000);

        this.restClient = RestClient.builder()
                .baseUrl(catalogUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public Optional<CatalogVariantResponse> getVariant(Long variantId) {
        try {
            CatalogVariantResponse response = restClient.get()
                    .uri("/api/internal/catalog/variants/{id}", variantId)
                    .retrieve()
                    .body(CatalogVariantResponse.class);
            return Optional.ofNullable(response);
        } catch (Exception e) {
            log.warn("Unable to retrieve catalog variant {} from catalog service: {}", variantId, e.getMessage());
            return Optional.empty();
        }
    }
}
