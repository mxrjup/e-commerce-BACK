package com.ecommerce.order.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpCatalogClient implements CatalogClient {

    private final RestClient restClient;

    public HttpCatalogClient(@Value("${app.catalog-service.url:http://localhost:8081}") String catalogUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(catalogUrl)
                .build();
    }

    @Override
    public CatalogVariantResponse getVariant(Long variantId) {
        try {
            return restClient.get()
                    .uri("/api/internal/catalog/variants/{id}", variantId)
                    .retrieve()
                    .body(CatalogVariantResponse.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Impossible de récupérer la variante " + variantId + " depuis le catalogue : " + e.getMessage(), e);
        }
    }
}
