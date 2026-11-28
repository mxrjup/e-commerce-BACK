package com.ecommerce.order.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class HttpCatalogClient implements CatalogClient {

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
    public List<CatalogProductMapping> getProductMappings(List<Long> ids) {
        return restClient.get()
                .uri(builder -> builder.path("/api/internal/catalog/variants")
                        .queryParam("ids", ids.stream().map(String::valueOf).collect(Collectors.joining(","))).build())
                .retrieve().body(new ParameterizedTypeReference<List<CatalogProductMapping>>() {});
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
