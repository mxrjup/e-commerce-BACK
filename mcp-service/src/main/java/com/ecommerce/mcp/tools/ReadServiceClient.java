package com.ecommerce.mcp.tools;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;

/** Only GET is available. URLs and credentials come from server configuration, never tool arguments. */
@Component
public class ReadServiceClient {
    private final RestClient catalog;
    private final RestClient orders;
    private final RestClient users;
    private final String usersKey;

    public ReadServiceClient(RestClient.Builder builder,
            @Value("${mcp.services.catalog-url:http://localhost:8081}") String catalogUrl,
            @Value("${mcp.services.orders-url:http://localhost:8084}") String ordersUrl,
            @Value("${mcp.services.users-url:http://localhost:8082}") String usersUrl,
            @Value("${mcp.services.users-key:}") String usersKey) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.catalog = builder.clone().requestFactory(factory).baseUrl(catalogUrl).build();
        this.orders = builder.clone().requestFactory(factory).baseUrl(ordersUrl).build();
        this.users = builder.clone().requestFactory(factory).baseUrl(usersUrl).build();
        this.usersKey = usersKey;
    }

    public <T> T catalog(String path, Class<T> type) { return get(catalog, path, type, false); }
    public <T> T orders(String path, Class<T> type) { return get(orders, path, type, false); }
    public <T> T users(String path, Class<T> type) {
        if (usersKey.isBlank()) throw new IllegalStateException("User read service is not configured");
        return get(users, path, type, true);
    }

    private <T> T get(RestClient client, String path, Class<T> type, boolean authenticate) {
        try {
            var request = client.get().uri(path);
            if (authenticate) request.header("X-MCP-Service-Key", usersKey);
            T body = request.retrieve().body(type);
            if (body == null) throw new IllegalStateException("Read service returned an empty response");
            return body;
        } catch (RestClientResponseException ex) {
            // Do not expose upstream response bodies, URLs, personal data or credentials in tool errors.
            throw new IllegalStateException(ex.getStatusCode().value() == 404
                    ? "Requested record was not found" : "Read service rejected the request");
        } catch (RestClientException ex) {
            throw new IllegalStateException("Read service is unavailable or returned an invalid response");
        }
    }
}
