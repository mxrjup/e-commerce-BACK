package com.ecommerce.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import com.ecommerce.mcp.token.*;
import java.util.Set;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpHttpTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    protected McpTokenService tokenService;

    protected String token;

    @BeforeEach
    void issueToken() {
        token = tokenService.create(new CreateMcpTokenRequest("HTTP test",
                Set.of(McpScope.CATALOG_READ, McpScope.ORDERS_READ), null), "test").token();
    }

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

        HttpResponse<String> deleted = http.send(request("/mcp")
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

    @Test
    void shouldRejectMissingUnknownAndJwtTokensOnEveryTransportMethod() throws Exception {
        for (String presented : new String[]{null, "mcp_unknown", "eyJhbGciOiJSUzI1NiJ9.e30.sig"}) {
            for (String method : new String[]{"GET", "POST", "DELETE"}) {
                var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/mcp"))
                        .timeout(Duration.ofSeconds(10));
                if (presented != null) builder.header("Authorization", "Bearer " + presented);
                var response = http.send(builder.method(method, HttpRequest.BodyPublishers.noBody()).build(),
                        HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(401);
                assertThat(response.headers().firstValue("WWW-Authenticate")).contains("Bearer realm=\"mcp\"");
            }
        }
    }

    @Test
    void shouldRejectRevokedAndExpiredTokensEvenWithAnExistingSession() throws Exception {
        for (boolean expire : new boolean[]{false, true}) {
            var created = tokenService.create(new CreateMcpTokenRequest("lifecycle",
                    Set.of(McpScope.CATALOG_READ), null), "test");
            token = created.token();
            String session = initializeSession();
            if (expire) {
                jdbc.update("UPDATE jeton_mcp SET date_expiration = ? WHERE id = ?",
                        java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)), created.metadata().id());
            } else {
                tokenService.revoke(created.metadata().id(), "test");
            }
            assertThat(post(session, "{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"ping\"}").statusCode())
                    .isEqualTo(401);
        }
    }

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    protected String initializeSession() throws Exception {
        var response = post(null, """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{
                  "protocolVersion":"2025-03-26","capabilities":{},
                  "clientInfo":{"name":"auth-test","version":"1.0"}}}
                """);
        assertThat(response.statusCode()).isEqualTo(200);
        String session = response.headers().firstValue("Mcp-Session-Id").orElseThrow();
        assertThat(post(session, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}").statusCode())
                .isEqualTo(202);
        return session;
    }

    protected void assertTools(JsonNode tools) {
        assertThat(tools.isArray()).isTrue();
        assertThat(tools.isEmpty()).isTrue();
    }

    protected HttpResponse<String> post(String session, String json) throws Exception {
        HttpRequest.Builder builder = request("/mcp")
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
                .header("Authorization", "Bearer " + token)
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
