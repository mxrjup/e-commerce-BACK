package com.ecommerce.mcp;

import com.ecommerce.mcp.token.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceReadToolsHttpTest extends McpHttpTest {
    static final List<String> requests = new CopyOnWriteArrayList<>();
    static final HttpServer upstream;
    static volatile int responseStatus = 200;
    static {
        try {
            upstream = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            upstream.createContext("/", exchange -> {
                String path = exchange.getRequestURI().toString();
                requests.add(exchange.getRequestMethod() + " " + path);
                String body;
                if (path.startsWith("/api/internal/users")) {
                    if (!"test-service-key".equals(exchange.getRequestHeaders().getFirst("X-MCP-Service-Key")))
                        throw new AssertionError("Missing service credential");
                    body = path.contains("?")
                            ? "{\"items\":[],\"page\":0,\"size\":10,\"totalElements\":0,\"totalPages\":0}"
                            : "{\"id\":1,\"email\":\"user@test\",\"firstName\":\"A\",\"lastName\":\"B\",\"secret\":\"hidden\"}";
                } else if (path.contains("variants")) {
                    body = """
                            {"id":1,"productId":2,"productSlug":"mouse","sku":"SKU-1","designation":"Mouse",
                            "unitPriceExclTax":10.50,"vatRate":20,"stock":7,"status":"PUBLIE","available":true,
                            "imageUrl":null,"purchasePriceExclTax":999}
                            """;
                    if (path.contains("?")) body = "[" + body + "]";
                } else {
                    body = """
                            {"id":1,"orderNumber":"ORD-1","userId":1,"status":"NEW","deliveryMode":"PICKUP",
                            "shippingAddressId":null,"totalExclTax":10.50,"totalInclTax":12.60,
                            "createdAt":"2026-11-12T10:00:00Z","items":[]}
                            """;
                    if (path.contains("?")) body = "[" + body + "]";
                }
                if (responseStatus != 200) body = "private upstream diagnostics";
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(responseStatus, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            upstream.start();
        } catch (Exception ex) { throw new ExceptionInInitializerError(ex); }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        for (String service : List.of("catalog", "orders", "users"))
            registry.add("mcp.services." + service + "-url", () -> "http://localhost:" + upstream.getAddress().getPort());
        registry.add("mcp.services.users-key", () -> "test-service-key");
    }

    @AfterAll
    static void stop() { upstream.stop(0); }

    @Test
    void readsAllServicesWithStructuredResultsAndOnlyGetRequests() throws Exception {
        token = tokenService.create(new CreateMcpTokenRequest("all reads", Set.of(McpScope.values()), null), "test").token();
        String session = initializeSession();
        requests.clear();
        for (var entry : Map.<String, Map<String, Object>>of(
                "get_catalog_variant", Map.of("id", 1),
                "get_catalog_variants", Map.of("ids", List.of(1, 2)),
                "get_order", Map.of("id", 1),
                "list_user_orders", Map.of("userId", 1, "status", "NEW"),
                "get_user", Map.of("id", 1),
                "list_users", Map.of("page", 0, "size", 10)).entrySet()) {
            JsonNode result = call(session, entry.getKey(), entry.getValue());
            assertThat(result.path("isError").asBoolean()).as(result.toString()).isFalse();
            assertThat(result.path("structuredContent").isObject()).as(result.toString()).isTrue();
            assertThat(result.toString()).doesNotContain("hidden", "purchasePriceExclTax");
        }
        assertThat(requests).containsExactlyInAnyOrder(
                "GET /api/internal/catalog/variants/1", "GET /api/internal/catalog/variants?ids=1,2",
                "GET /api/admin/orders/1", "GET /api/admin/orders?userId=1&status=NEW",
                "GET /api/internal/users/1", "GET /api/internal/users?page=0&size=10");
    }

    @Test
    void rejectsInvalidInputsAndMissingScopesBeforeCallingUpstream() throws Exception {
        String session = initializeSession();
        requests.clear();
        for (var args : List.of(Map.<String, Object>of(), Map.<String, Object>of("id", 0), Map.<String, Object>of("id", -1)))
            assertThat(call(session, "get_order", args).path("isError").asBoolean()).isTrue();
        assertThat(call(session, "get_catalog_variants", Map.of("ids", List.of())).path("isError").asBoolean()).isTrue();
        assertThat(call(session, "get_user", Map.of("id", 1)).path("isError").asBoolean()).isTrue();
        token = tokenService.create(new CreateMcpTokenRequest("users only", Set.of(McpScope.USERS_READ), null), "test").token();
        assertThat(call(session, "get_order", Map.of("id", 1)).path("isError").asBoolean()).isTrue();
        assertThat(call(session, "get_catalog_variant", Map.of("id", 1)).path("isError").asBoolean()).isTrue();
        assertThat(call(session, "list_users", Map.of("page", 0, "size", 101)).path("isError").asBoolean()).isTrue();
        assertThat(requests).isEmpty();
    }

    @Test
    void upstreamFailuresAreToolErrorsWithoutLeakingResponseBodies() throws Exception {
        String session = initializeSession();
        try {
            for (int status : List.of(404, 503)) {
                responseStatus = status;
                JsonNode result = call(session, "get_order", Map.of("id", 1));
                assertThat(result.path("isError").asBoolean()).isTrue();
                assertThat(result.toString()).doesNotContain("private upstream diagnostics", "test-service-key");
            }
        } finally { responseStatus = 200; }
    }

    private JsonNode call(String session, String name, Map<String, Object> args) throws Exception {
        return rpcBody(post(session, new ObjectMapper().writeValueAsString(Map.of("jsonrpc", "2.0", "id", 40,
                "method", "tools/call", "params", Map.of("name", name, "arguments", args))))).path("result");
    }
}
