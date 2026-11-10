package com.ecommerce.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpHttpTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    @Test
    void shouldInitializeUseAndTerminateAStreamableHttpSession() throws Exception {
        HttpResponse<String> initialized = post(null, """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{
                  "protocolVersion":"2025-03-26","capabilities":{},
                  "clientInfo":{"name":"integration-test","version":"1.0.0"}}}
                """);
        assertThat(initialized.statusCode()).isEqualTo(200);
        JsonNode result = rpcBody(initialized).path("result");
        assertThat(result.path("serverInfo").path("name").asText()).isEqualTo("ecommerce-mcp");
        assertThat(result.path("capabilities").has("tools")).isTrue();
        assertThat(result.path("capabilities").has("resources")).isFalse();
        String session = initialized.headers().firstValue("Mcp-Session-Id").orElseThrow();

        assertThat(post(session, """
                {"jsonrpc":"2.0","method":"notifications/initialized"}
                """).statusCode()).isEqualTo(202);

        HttpResponse<String> ping = post(session, """
                {"jsonrpc":"2.0","id":2,"method":"ping"}
                """);
        assertThat(ping.statusCode()).isEqualTo(200);
        assertThat(rpcBody(ping).has("result")).isTrue();

        HttpResponse<String> tools = post(session, """
                {"jsonrpc":"2.0","id":3,"method":"tools/list"}
                """);
        assertThat(tools.statusCode()).isEqualTo(200);
        assertTools(rpcBody(tools).path("result").path("tools"));

        HttpResponse<String> deleted = http.send(request("/api/mcp")
                .header("Mcp-Session-Id", session).DELETE().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(deleted.statusCode()).isEqualTo(200);
        assertThat(post(session, """
                {"jsonrpc":"2.0","id":4,"method":"ping"}
                """).statusCode()).isEqualTo(404);
    }

    @Test
    void shouldExposeHealthButNotTheLegacySseTransport() throws Exception {
        HttpResponse<String> health = http.send(request("/actuator/health").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(health.body()).path("status").asText()).isEqualTo("UP");
        HttpResponse<String> legacy = http.send(request("/sse").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(legacy.statusCode()).isEqualTo(404);
    }

    protected void assertTools(JsonNode tools) {
        assertThat(tools.isArray()).isTrue();
        assertThat(tools.isEmpty()).isTrue();
    }

    protected HttpResponse<String> post(String session, String json) throws Exception {
        HttpRequest.Builder builder = request("/api/mcp")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("MCP-Protocol-Version", "2025-03-26");
        if (session != null) {
            builder.header("Mcp-Session-Id", session);
        }
        return http.send(builder.POST(HttpRequest.BodyPublishers.ofString(json)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10));
    }

    protected JsonNode rpcBody(HttpResponse<String> response) throws Exception {
        String body = response.body();
        if (response.headers().firstValue("Content-Type").orElse("").startsWith("text/event-stream")) {
            body = body.lines().filter(line -> line.startsWith("data:"))
                    .map(line -> line.substring(5).trim()).findFirst().orElseThrow();
        }
        return objectMapper.readTree(body);
    }
}
