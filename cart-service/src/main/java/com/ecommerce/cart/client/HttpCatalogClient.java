package com.ecommerce.cart.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Component
public class HttpCatalogClient implements CatalogClient {

    private final RestClient restClient;

    public HttpCatalogClient(@Value("${app.catalog-service.url:http://localhost:8081}") String catalogUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(catalogUrl)
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
            return Optional.empty();
        }
    }
}
