package com.ecommerce.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("demo-stock")
class McpDemoStockHttpTest extends McpHttpTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void shouldUseTheCurrentRequestScopesAndNeverReturnDataForAnUnauthorizedTool() throws Exception {
        String session = initializeSession();
        token = tokenService.create(new com.ecommerce.mcp.token.CreateMcpTokenRequest("catalog only",
                java.util.Set.of(com.ecommerce.mcp.token.McpScope.CATALOG_READ), null), "test").token();
        var denied = post(session, """
                {"jsonrpc":"2.0","id":20,"method":"tools/call","params":{
                  "name":"get_pending_shipments","arguments":{"productName":"Logitech G Pro"}}}
                """);
        assertThat(denied.statusCode()).isEqualTo(200);
        var result = rpcBody(denied).path("result");
        assertThat(result.path("isError").asBoolean()).isTrue();
        assertThat(result.toString()).contains("orders:read").doesNotContain("pendingUnits");
        var allowed = post(session, """
                {"jsonrpc":"2.0","id":21,"method":"tools/call","params":{
                  "name":"get_product_stock","arguments":{"productName":"Logitech G Pro"}}}
                """);
        assertThat(rpcBody(allowed).path("result").path("isError").asBoolean()).isFalse();
        assertThat(rpcBody(allowed).toString()).contains("24");
    }

    @Override
    protected void assertTools(JsonNode tools) {
        assertThat(tools.isArray()).isTrue();
        assertThat(tools.size()).isEqualTo(2);
        assertThat(tools.toString()).contains("get_product_stock", "get_pending_shipments");
        tools.forEach(tool -> {
            assertThat(tool.path("inputSchema").path("required").toString()).contains("productName");
            assertThat(tool.path("annotations").path("readOnlyHint").asBoolean()).isTrue();
        });
    }

    @Test
    void shouldLookUpSeedStockAndPublishTheEditablePromptOverHttp() throws Exception {
        var initialized = post(null, """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{
                  "protocolVersion":"2025-03-26","capabilities":{},
                  "clientInfo":{"name":"stock-test","version":"1.0.0"}}}
                """);
        assertThat(initialized.statusCode()).isEqualTo(200);
        assertThat(rpcBody(initialized).path("result").path("capabilities").has("prompts")).isTrue();
        String session = initialized.headers().firstValue("Mcp-Session-Id").orElseThrow();
        assertThat(post(session, """
                {"jsonrpc":"2.0","method":"notifications/initialized"}
                """).statusCode()).isEqualTo(202);

        record Example(String query, String status, int matches, Integer quantity) {}
        for (Example example : List.of(
                new Example("  logitech g pro  ", "FOUND", 1, 24),
                new Example("DeathAdder", "FOUND", 1, 15),
                new Example("Logitech", "MULTIPLE_MATCHES", 2, null),
                new Example("Sony WH-1000XM5", "FOUND", 1, 0),
                new Example("nonexistent product", "NOT_FOUND", 0, null))) {
            var response = post(session, json.writeValueAsString(Map.of(
                    "jsonrpc", "2.0", "id", 2, "method", "tools/call", "params", Map.of(
                            "name", "get_product_stock", "arguments", Map.of("productName", example.query())))));
            assertThat(response.statusCode()).isEqualTo(200);
            JsonNode tool = rpcBody(response).path("result");
            assertThat(tool.path("isError").asBoolean()).isFalse();
            JsonNode stock = json.readTree(tool.path("content").get(0).path("text").asText());
            assertThat(stock.path("source").asText()).isEqualTo("seed");
            assertThat(stock.path("status").asText()).isEqualTo(example.status());
            assertThat(stock.path("matches").size()).isEqualTo(example.matches());
            if (example.quantity() != null) {
                assertThat(stock.path("matches").get(0).path("stock").asInt()).isEqualTo(example.quantity());
            }
        }

        var invalid = post(session, """
                {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{
                  "name":"get_product_stock","arguments":{"productName":" "}}}
                """);
        assertThat(rpcBody(invalid).path("result").path("isError").asBoolean()).isTrue();

        var prompts = post(session, """
                {"jsonrpc":"2.0","id":4,"method":"prompts/list"}
                """);
        assertThat(rpcBody(prompts).path("result").path("prompts").size()).isEqualTo(3);
        assertThat(rpcBody(prompts).path("result").path("prompts").toString())
                .contains("product_stock", "pending_shipments", "stock_coverage");
        var prompt = post(session, """
                {"jsonrpc":"2.0","id":5,"method":"prompts/get","params":{
                  "name":"product_stock","arguments":{"productName":"Logitech G Pro"}}}
                """);
        String text = rpcBody(prompt).path("result").path("messages").get(0).path("content").path("text").asText();
        assertThat(text).contains("Logitech G Pro", "get_product_stock", "seed/demo");
        assertThat(text).doesNotContain("{productName}");

        for (String promptName : List.of("pending_shipments", "stock_coverage")) {
            var response = post(session, json.writeValueAsString(Map.of(
                    "jsonrpc", "2.0", "id", 6, "method", "prompts/get", "params", Map.of(
                            "name", promptName, "arguments", Map.of("productName", "Logitech G Pro")))));
            String rendered = rpcBody(response).path("result").path("messages").get(0).path("content").path("text").asText();
            assertThat(rendered).contains("Logitech G Pro", "get_pending_shipments", "seed/demo")
                    .doesNotContain("{productName}");
        }

        var shipmentResponse = post(session, """
                {"jsonrpc":"2.0","id":7,"method":"tools/call","params":{
                  "name":"get_pending_shipments","arguments":{"productName":"Logitech G Pro"}}}
                """);
        JsonNode shipmentResult = rpcBody(shipmentResponse).path("result");
        assertThat(shipmentResult.path("isError").asBoolean()).isFalse();
        JsonNode shipments = json.readTree(shipmentResult.path("content").get(0).path("text").asText());
        assertThat(shipments.path("source").asText()).isEqualTo("seed");
        assertThat(shipments.path("matches").get(0).path("pendingUnits").asInt()).isEqualTo(7);
        assertThat(shipments.path("matches").get(0).path("pendingOrderCount").asInt()).isEqualTo(2);
    }
}
