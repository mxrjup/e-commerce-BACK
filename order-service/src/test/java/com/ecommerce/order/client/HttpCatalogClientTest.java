package com.ecommerce.order.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;

class HttpCatalogClientTest {
    @Test
    void resolvesProductIdsUsingExistingBatchContractAndIgnoresExtraFields() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var query = new AtomicReference<String>();
        server.createContext("/api/internal/catalog/variants", exchange -> {
            query.set(exchange.getRequestURI().getQuery());
            byte[] body = "[{\"id\":1,\"productId\":42,\"designation\":\"Product - Blue\",\"stock\":3}]"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var stream = exchange.getResponseBody()) { stream.write(body); }
        });
        server.start();
        try {
            var client = new HttpCatalogClient("http://127.0.0.1:" + server.getAddress().getPort());
            assertThat(client.getProductMappings(List.of(1L, 2L))).containsExactly(new CatalogProductMapping(1L, 42L));
            assertThat(query.get()).isEqualTo("ids=1,2");
        } finally {
            server.stop(0);
        }
    }
}
