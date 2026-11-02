package com.ecommerce.gateway;

import com.ecommerce.gateway.filter.IdentityHeadersFilter;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

/**
 * Exercises the gateway end to end: a request enters, the rules decide, and a
 * fake service records what actually reached it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewaySecurityTest {

    private static final MockWebServer BACKEND = new MockWebServer();

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private ReactiveJwtDecoder jwtDecoder;

    @BeforeAll
    static void startBackend() throws IOException {
        BACKEND.start();
    }

    @AfterAll
    static void stopBackend() throws IOException {
        BACKEND.shutdown();
    }

    /**
     * Overrides are registered here rather than in a test application.yml: such
     * a file would shadow the one of the module, routes included.
     */
    @DynamicPropertySource
    static void routesPointAtTheFakeService(DynamicPropertyRegistry registry) {
        String uri = "http://" + BACKEND.getHostName() + ":" + BACKEND.getPort();
        registry.add("CART_SERVICE_URI", () -> uri);
        registry.add("ORDER_SERVICE_URI", () -> uri);
        // Le decodeur de jetons est mocke : ces URL ne sont jamais appelees,
        // elles evitent seulement un appel reseau au demarrage.
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> "http://localhost:1/realms/test");
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://localhost:1/realms/test/protocol/openid-connect/certs");
    }

    @Test
    void shouldLetAGuestReachTheCartWithoutAToken() throws InterruptedException {
        BACKEND.enqueue(ok());

        webTestClient.get().uri("/api/cart")
                .exchange()
                .expectStatus().isOk();

        RecordedRequest forwarded = BACKEND.takeRequest();
        assertThat(forwarded.getPath()).isEqualTo("/api/cart");
        assertThat(forwarded.getHeader(IdentityHeadersFilter.SUBJECT_HEADER)).isNull();
    }

    @Test
    void shouldStripAnIdentityHeaderComingFromTheClient() throws InterruptedException {
        BACKEND.enqueue(ok());

        webTestClient.get().uri("/api/cart")
                .header("X-User-Id", "42")
                .header(IdentityHeadersFilter.SUBJECT_HEADER, "someone-else")
                .exchange()
                .expectStatus().isOk();

        RecordedRequest forwarded = BACKEND.takeRequest();
        assertThat(forwarded.getHeader("X-User-Id")).isNull();
        assertThat(forwarded.getHeader(IdentityHeadersFilter.SUBJECT_HEADER)).isNull();
    }

    @Test
    void shouldForwardTheSubjectOfTheTokenAndNothingTheClientClaimed() throws InterruptedException {
        givenTokenFor("11111111-2222-3333-4444-555555555555", "client");
        BACKEND.enqueue(ok());

        webTestClient.get().uri("/api/cart")
                .header("Authorization", "Bearer any-value-the-decoder-is-mocked")
                .header("X-User-Id", "42")
                .header(IdentityHeadersFilter.SUBJECT_HEADER, "someone-else")
                .exchange()
                .expectStatus().isOk();

        RecordedRequest forwarded = BACKEND.takeRequest();
        assertThat(forwarded.getHeader(IdentityHeadersFilter.SUBJECT_HEADER))
                .isEqualTo("11111111-2222-3333-4444-555555555555");
        assertThat(forwarded.getHeader("X-User-Id")).isNull();
    }

    @Test
    void shouldKeepTheGuestCartTokenUntouched() throws InterruptedException {
        BACKEND.enqueue(ok());

        webTestClient.get().uri("/api/cart")
                .header("X-Session-Token", "guest-session-123")
                .exchange()
                .expectStatus().isOk();

        assertThat(BACKEND.takeRequest().getHeader("X-Session-Token")).isEqualTo("guest-session-123");
    }

    @Test
    void shouldRejectAnOrderRequestWithoutAToken() {
        webTestClient.get().uri("/api/orders")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.error").isEqualTo("Unauthorized")
                .jsonPath("$.timestamp").exists();
    }

    @Test
    void shouldRejectAnAdminRequestFromSomeoneWithoutTheRole() {
        givenTokenFor("a-client", "client");

        webTestClient.get().uri("/api/admin/orders")
                .header("Authorization", "Bearer any-value-the-decoder-is-mocked")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.status").isEqualTo(403);
    }

    @Test
    void shouldRouteAnAdminRequestFromACollaborator() throws InterruptedException {
        givenTokenFor("a-collaborator", "client", "collaborator");
        BACKEND.enqueue(ok());

        webTestClient.get().uri("/api/admin/orders")
                .header("Authorization", "Bearer any-value-the-decoder-is-mocked")
                .exchange()
                .expectStatus().isOk();

        assertThat(BACKEND.takeRequest().getPath()).isEqualTo("/api/admin/orders");
    }

    @Test
    void shouldAnswerAPreflightFromTheBackOfficeOrigin() {
        webTestClient.options().uri("/api/cart")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:5173")
                .expectHeader().valueEquals("Access-Control-Allow-Credentials", "true");
    }

    @Test
    void shouldRefuseAPreflightFromAnUnknownOrigin() {
        webTestClient.options().uri("/api/cart")
                .header("Origin", "http://evil.example")
                .header("Access-Control-Request-Method", "POST")
                .exchange()
                .expectStatus().isForbidden();
    }

    private void givenTokenFor(String subject, String... realmRoles) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(subject)
                .audience(List.of("ecommerce-api"))
                .claim("realm_access", Map.of("roles", List.of(realmRoles)))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        given(jwtDecoder.decode(anyString())).willReturn(Mono.just(jwt));
    }

    private static MockResponse ok() {
        return new MockResponse()
                .setResponseCode(200)
                .setBody("{}")
                .addHeader("Content-Type", "application/json");
    }
}
