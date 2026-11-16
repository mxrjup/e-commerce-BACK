package com.ecommerce.users.keycloak;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

class KeycloakTokenProviderTest {

    private static final String TOKEN_URL = "http://keycloak.test/realms/ecommerce/protocol/openid-connect/token";

    private MockRestServiceServer keycloak;
    private MutableClock clock;
    private KeycloakTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://keycloak.test");
        keycloak = MockRestServiceServer.bindTo(builder).build();
        clock = new MutableClock(Instant.parse("2026-11-09T10:00:00Z"));
        KeycloakAdminProperties properties =
                new KeycloakAdminProperties("http://keycloak.test", "ecommerce", "user-service", "s3cret");
        tokenProvider = new KeycloakTokenProvider(builder.build(), properties, clock);
    }

    @Test
    void shouldRequestATokenWithTheClientCredentialsGrant() {
        keycloak.expect(requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formDataContains(Map.of(
                        "grant_type", "client_credentials",
                        "client_id", "user-service",
                        "client_secret", "s3cret")))
                .andRespond(token("first", 300));

        assertThat(tokenProvider.accessToken()).isEqualTo("first");
        keycloak.verify();
    }

    @Test
    void shouldReuseTheTokenUntilShortlyBeforeItExpires() {
        keycloak.expect(ExpectedCount.once(), requestTo(TOKEN_URL)).andRespond(token("first", 300));
        keycloak.expect(ExpectedCount.once(), requestTo(TOKEN_URL)).andRespond(token("second", 300));

        assertThat(tokenProvider.accessToken()).isEqualTo("first");
        clock.advance(Duration.ofSeconds(300).minus(KeycloakTokenProvider.EXPIRY_MARGIN).minusSeconds(1));
        assertThat(tokenProvider.accessToken()).isEqualTo("first");

        clock.advance(Duration.ofSeconds(1));
        assertThat(tokenProvider.accessToken()).isEqualTo("second");
        keycloak.verify();
    }

    @Test
    void shouldRequestANewTokenOnceInvalidated() {
        keycloak.expect(ExpectedCount.once(), requestTo(TOKEN_URL)).andRespond(token("first", 300));
        keycloak.expect(ExpectedCount.once(), requestTo(TOKEN_URL)).andRespond(token("second", 300));

        assertThat(tokenProvider.accessToken()).isEqualTo("first");
        tokenProvider.invalidate();
        assertThat(tokenProvider.accessToken()).isEqualTo("second");
    }

    @Test
    void shouldReportAWrongSecretAsAnUnavailableKeycloak() {
        keycloak.expect(requestTo(TOKEN_URL)).andRespond(withUnauthorizedRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"unauthorized_client\",\"error_description\":\"Invalid client secret\"}"));

        assertThatThrownBy(tokenProvider::accessToken)
                .isInstanceOf(KeycloakUnavailableException.class)
                .hasMessageNotContaining("s3cret");
    }

    @Test
    void shouldNotCacheAFailure() {
        keycloak.expect(ExpectedCount.once(), requestTo(TOKEN_URL)).andRespond(withServerError());
        keycloak.expect(ExpectedCount.once(), requestTo(TOKEN_URL)).andRespond(token("after-outage", 300));

        assertThatThrownBy(tokenProvider::accessToken).isInstanceOf(KeycloakUnavailableException.class);
        assertThat(tokenProvider.accessToken()).isEqualTo("after-outage");
    }

    private static ResponseCreator token(String value, long expiresIn) {
        return withSuccess("{\"access_token\":\"" + value + "\",\"expires_in\":" + expiresIn
                + ",\"token_type\":\"Bearer\"}", MediaType.APPLICATION_JSON);
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
