package com.ecommerce.mcp;

import com.ecommerce.mcp.token.TokenGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tokens end to end: issued by the back-office API, stored by the real Flyway
 * schema, then presented by an MCP client to the transport endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
class McpTokenApiTest {

    private static final String ADMIN = "/api/admin/mcp/tokens";
    private static final Instant NOW = Instant.parse("2026-11-09T10:00:00Z");
    private static final String ADMIN_SUB = "aaaaaaaa-0000-0000-0000-000000000001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MutableClock clock;

    @TestConfiguration
    static class FixedClock {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(NOW);
        }
    }

    @BeforeEach
    void reset() {
        jdbcTemplate.update("DELETE FROM jeton_mcp");
        clock.set(NOW);
    }

    @Nested
    class Issuing {

        @Test
        void shouldReturnTheTokenOnceAndStoreOnlyItsFingerprint() throws Exception {
            JsonNode created = create("""
                    {"name":"  Claude Desktop - Marius ","scopes":["orders:read","catalog:read"]}""")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.name").value("Claude Desktop - Marius"))
                    .andExpect(jsonPath("$.token").value(matchesPattern("mcp_[A-Za-z0-9_-]{43}")))
                    .andExpect(jsonPath("$.scopes", contains("catalog:read", "orders:read")))
                    .andExpect(jsonPath("$.status").value("active"))
                    .andExpect(jsonPath("$.createdBy").value(ADMIN_SUB))
                    .andExpect(jsonPath("$.createdAt").value("2026-11-09T10:00:00Z"))
                    .andExpect(jsonPath("$.expiresAt").doesNotExist())
                    .andReturn().getResponse().getContentAsString().transform(McpTokenApiTest.this::json);

            String token = created.get("token").asText();
            assertThat(created.get("prefix").asText()).isEqualTo(token.substring(0, 12));

            String stored = jdbcTemplate.queryForObject("SELECT empreinte || '|' || portees FROM jeton_mcp",
                    String.class);
            assertThat(stored).isEqualTo(TokenGenerator.hash(token) + "|catalog:read orders:read");
            assertThat(stored).doesNotContain(token);

            mockMvc.perform(get(ADMIN + "/" + created.get("id").asLong()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").doesNotExist())
                    .andExpect(jsonPath("$.prefix").value(token.substring(0, 12)));
        }

        @Test
        void shouldIssueADifferentTokenEachTime() throws Exception {
            String first = tokenWith("\"catalog:read\"");
            String second = tokenWith("\"catalog:read\"");
            assertThat(first).isNotEqualTo(second);
        }

        @Test
        void shouldListTheNewestFirstWithoutAnyToken() throws Exception {
            create("{\"name\":\"older\",\"scopes\":[\"users:read\"]}");
            clock.advance(Duration.ofMinutes(5));
            create("{\"name\":\"newer\",\"scopes\":[\"users:read\"]}");

            mockMvc.perform(get(ADMIN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[*].name", contains("newer", "older")))
                    .andExpect(jsonPath("$[0].token").doesNotExist());
        }

        @Test
        void shouldValidateTheRequest() throws Exception {
            create("{\"name\":\"\",\"scopes\":[],\"expiresAt\":\"2020-01-01T00:00:00Z\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details", containsInAnyOrder(
                            "name must not be blank",
                            "scopes must contain at least one scope",
                            "expiresAt must be in the future")));
        }

        @Test
        void shouldRejectANullScope() throws Exception {
            create("{\"name\":\"x\",\"scopes\":[null]}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details", contains("scopes must not contain null")));
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM jeton_mcp", Long.class)).isZero();
        }

        @Test
        void shouldRejectAnUnknownScope() throws Exception {
            create("{\"name\":\"x\",\"scopes\":[\"orders:write\"]}").andExpect(status().isBadRequest());
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM jeton_mcp", Long.class)).isZero();
        }

        @Test
        void shouldAnswer404ForAnUnknownToken() throws Exception {
            mockMvc.perform(get(ADMIN + "/999")).andExpect(status().isNotFound());
            mockMvc.perform(post(ADMIN + "/999/revoke")).andExpect(status().isNotFound());
        }
    }

    @Nested
    class Authenticating {

        @Test
        void shouldLetAnActiveTokenThroughAndExposeItToTheTool() throws Exception {
            String token = tokenWith("\"catalog:read\"");

            mockMvc.perform(post("/mcp/message").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value("token"));
            mockMvc.perform(get("/sse").header(HttpHeaders.AUTHORIZATION, "bearer " + token))
                    .andExpect(status().isOk());
        }

        @Test
        void shouldRejectARequestWithoutAToken() throws Exception {
            mockMvc.perform(get("/sse"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"mcp\""))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.message").value("A valid MCP token is required"));
        }

        @Test
        void shouldRejectAnUnknownTokenOrAKeycloakJwt() throws Exception {
            tokenWith("\"catalog:read\"");
            for (String presented : new String[]{"mcp_not-a-token-we-issued", "eyJhbGciOiJSUzI1NiJ9.e30.sig",
                    "Basic dXNlcjpwYXNz"}) {
                mockMvc.perform(post("/mcp/message").header(HttpHeaders.AUTHORIZATION, presented.startsWith("Basic")
                                ? presented : "Bearer " + presented))
                        .andExpect(status().isUnauthorized());
            }
        }

        @Test
        void shouldRefuseAToolOutsideTheScopesOfTheToken() throws Exception {
            String token = tokenWith("\"catalog:read\"");

            mockMvc.perform(post("/mcp").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("This MCP token lacks the scope 'orders:read'"));
        }

        @Test
        void shouldStopAcceptingARevokedToken() throws Exception {
            JsonNode created = json(create("{\"name\":\"leaked\",\"scopes\":[\"catalog:read\"]}")
                    .andReturn().getResponse().getContentAsString());
            String token = created.get("token").asText();
            long id = created.get("id").asLong();

            clock.advance(Duration.ofMinutes(1));
            mockMvc.perform(post(ADMIN + "/" + id + "/revoke").header("X-Auth-Subject", ADMIN_SUB))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("revoked"))
                    .andExpect(jsonPath("$.revokedBy").value(ADMIN_SUB))
                    .andExpect(jsonPath("$.revokedAt").value("2026-11-09T10:01:00Z"));

            mockMvc.perform(post("/mcp/message").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized());

            // Une seconde revocation ne reecrit pas la premiere.
            clock.advance(Duration.ofMinutes(1));
            mockMvc.perform(post(ADMIN + "/" + id + "/revoke").header("X-Auth-Subject", "someone-else"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.revokedBy").value(ADMIN_SUB))
                    .andExpect(jsonPath("$.revokedAt").value("2026-11-09T10:01:00Z"));
        }

        @Test
        void shouldStopAcceptingATokenOnceExpired() throws Exception {
            String token = json(create("""
                    {"name":"demo","scopes":["catalog:read"],"expiresAt":"2026-11-09T11:00:00Z"}""")
                    .andReturn().getResponse().getContentAsString()).get("token").asText();

            clock.set(Instant.parse("2026-11-09T10:59:59Z"));
            mockMvc.perform(post("/mcp/message").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk());

            clock.set(Instant.parse("2026-11-09T11:00:00Z"));
            mockMvc.perform(post("/mcp/message").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get(ADMIN))
                    .andExpect(jsonPath("$[0].status").value("expired"));
        }

        @Test
        void shouldRecordTheLastUseAtMostOncePerMinute() throws Exception {
            String token = tokenWith("\"catalog:read\"");

            clock.set(Instant.parse("2026-11-09T10:10:00Z"));
            useToken(token);
            assertThat(lastUse()).isEqualTo("2026-11-09T10:10:00Z");

            clock.set(Instant.parse("2026-11-09T10:10:59Z"));
            useToken(token);
            assertThat(lastUse()).isEqualTo("2026-11-09T10:10:00Z");

            clock.set(Instant.parse("2026-11-09T10:11:01Z"));
            useToken(token);
            assertThat(lastUse()).isEqualTo("2026-11-09T10:11:01Z");
        }

        @Test
        void shouldLeaveTheBackOfficeApiToTheGateway() throws Exception {
            // /api/admin/mcp/** n'est pas un chemin MCP : aucun jeton MCP n'y est exige.
            mockMvc.perform(get(ADMIN)).andExpect(status().isOk());
        }

        private void useToken(String token) throws Exception {
            mockMvc.perform(post("/mcp/message").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk());
        }

        private String lastUse() throws Exception {
            return mockMvc.perform(get(ADMIN)).andReturn().getResponse().getContentAsString()
                    .transform(McpTokenApiTest.this::json).get(0).get("lastUsedAt").asText();
        }
    }

    private ResultActions create(String body) throws Exception {
        return mockMvc.perform(post(ADMIN).header("X-Auth-Subject", ADMIN_SUB)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String tokenWith(String scopes) throws Exception {
        return json(create("{\"name\":\"token\",\"scopes\":[" + scopes + "]}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("token").asText();
    }

    private JsonNode json(String content) {
        try {
            return objectMapper.readTree(content);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
