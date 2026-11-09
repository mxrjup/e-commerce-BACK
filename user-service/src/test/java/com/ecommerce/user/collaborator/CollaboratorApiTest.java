package com.ecommerce.user.collaborator;

import com.ecommerce.user.keycloak.KeycloakTokenProvider;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.result.JsonPathResultMatchers;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withCreatedEntity;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The collaborator API end to end: HTTP, validation, the service, and the
 * exact Admin API calls Keycloak receives, answered by a mock server.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CollaboratorApiTest {

    private static final String API = "/api/admin/users/collaborators";
    private static final String KC = "http://keycloak.test/admin/realms/ecommerce";

    private static final String ALICE = "aaaaaaaa-0000-0000-0000-000000000001";
    private static final String BOB = "bbbbbbbb-0000-0000-0000-000000000002";
    private static final String CAROL = "cccccccc-0000-0000-0000-000000000003";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MockRestServiceServer keycloak;

    @MockBean
    private KeycloakTokenProvider tokenProvider;

    @TestConfiguration
    static class MockKeycloak {

        private final RestClient.Builder builder = RestClient.builder().baseUrl("http://keycloak.test");

        @Bean
        MockRestServiceServer keycloakServer() {
            return MockRestServiceServer.bindTo(builder).build();
        }

        /** Replaces the real client, so that every call lands on the mock server. */
        @Bean
        @Primary
        RestClient mockedKeycloakRestClient(MockRestServiceServer keycloakServer) {
            return builder.build();
        }
    }

    @BeforeEach
    void setUp() {
        keycloak.reset();
        given(tokenProvider.accessToken()).willReturn("service-account-token");
    }

    @Nested
    class Listing {

        @Test
        void shouldListAdminsAndCollaboratorsOnceEachSortedByEmail() throws Exception {
            // Carol porte les deux roles directement : elle est admin, une seule fois.
            expectGet(KC + "/roles/admin/users?first=0&max=100&briefRepresentation=false",
                    "[" + user(CAROL, "carol@shop.test", true) + "]");
            expectGet(KC + "/roles/collaborator/users?first=0&max=100&briefRepresentation=false",
                    "[" + user(BOB, "bob@shop.test", false) + "," + user(CAROL, "carol@shop.test", true) + ","
                            + user(ALICE, "alice@shop.test", true) + "]");

            mockMvc.perform(get(API))
                    .andExpect(status().isOk())
                    .andExpect(jsonPathOf("$", hasSize(3)))
                    .andExpect(jsonPathOf("$[*].email", contains("alice@shop.test", "bob@shop.test", "carol@shop.test")))
                    .andExpect(jsonPathOf("$[*].role", contains("collaborator", "collaborator", "admin")))
                    .andExpect(jsonPathOf("$[1].enabled", is(false)))
                    .andExpect(jsonPathOf("$[0].createdAt", is("2026-11-01T08:00:00Z")));
            keycloak.verify();
        }

        @Test
        void shouldReadEveryPageOfARole() throws Exception {
            String fullPage = IntStream.range(0, 100)
                    .mapToObj(i -> user("id-" + i, "user" + String.format("%03d", i) + "@shop.test", true))
                    .collect(Collectors.joining(",", "[", "]"));
            expectGet(KC + "/roles/admin/users?first=0&max=100&briefRepresentation=false", "[]");
            expectGet(KC + "/roles/collaborator/users?first=0&max=100&briefRepresentation=false", fullPage);
            expectGet(KC + "/roles/collaborator/users?first=100&max=100&briefRepresentation=false",
                    "[" + user("id-100", "user100@shop.test", true) + "]");

            mockMvc.perform(get(API))
                    .andExpect(status().isOk())
                    .andExpect(jsonPathOf("$", hasSize(101)));
            keycloak.verify();
        }

        @Test
        void shouldSendTheServiceAccountToken() throws Exception {
            keycloak.expect(requestTo(KC + "/roles/admin/users?first=0&max=100&briefRepresentation=false"))
                    .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer service-account-token"))
                    .andRespond(json("[]"));
            expectGet(KC + "/roles/collaborator/users?first=0&max=100&briefRepresentation=false", "[]");

            mockMvc.perform(get(API)).andExpect(status().isOk());
            keycloak.verify();
        }

        @Test
        void shouldAnswer502WhenKeycloakFails() throws Exception {
            keycloak.expect(requestTo(KC + "/roles/admin/users?first=0&max=100&briefRepresentation=false"))
                    .andRespond(withServerError());

            mockMvc.perform(get(API))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPathOf("$.status", is(502)))
                    .andExpect(jsonPathOf("$.message",
                            is("The identity provider is unavailable, please retry later")));
        }
    }

    @Nested
    class Reading {

        @Test
        void shouldReturnACollaborator() throws Exception {
            expectCollaborator(BOB, "bob@shop.test", true, "collaborator");

            mockMvc.perform(get(API + "/" + BOB))
                    .andExpect(status().isOk())
                    .andExpect(jsonPathOf("$.id", is(BOB)))
                    .andExpect(jsonPathOf("$.firstName", is("First")))
                    .andExpect(jsonPathOf("$.role", is("collaborator")));
        }

        @Test
        void shouldHideAShopCustomer() throws Exception {
            expectGet(KC + "/users/" + ALICE, user(ALICE, "alice@shop.test", true));
            expectGet(KC + "/users/" + ALICE + "/role-mappings/realm", roles("default-roles-ecommerce", "client"));

            mockMvc.perform(get(API + "/" + ALICE))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPathOf("$.message",
                            is("Collaborator " + ALICE + " not found")));
        }

        @Test
        void shouldAnswer404ForAnUnknownAccount() throws Exception {
            keycloak.expect(requestTo(KC + "/users/unknown")).andRespond(withResourceNotFound()
                    .contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"User not found\"}"));

            mockMvc.perform(get(API + "/unknown")).andExpect(status().isNotFound());
        }
    }

    @Nested
    class Creation {

        private static final String BODY = """
                {"email":"New.Person@Shop.TEST","firstName":" New ","lastName":"Person",
                 "role":"admin","temporaryPassword":"Temp-pass-1"}""";

        @Test
        void shouldCreateTheAccountWithATemporaryPasswordAndItsRole() throws Exception {
            keycloak.expect(requestTo(KC + "/users"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(jsonPath("$.username").value("new.person@shop.test"))
                    .andExpect(jsonPath("$.email").value("new.person@shop.test"))
                    .andExpect(jsonPath("$.firstName").value("New"))
                    .andExpect(jsonPath("$.enabled").value(true))
                    .andExpect(jsonPath("$.emailVerified").value(true))
                    .andExpect(jsonPath("$.credentials[0].type").value("password"))
                    .andExpect(jsonPath("$.credentials[0].value").value("Temp-pass-1"))
                    .andExpect(jsonPath("$.credentials[0].temporary").value(true))
                    .andRespond(withCreatedEntity(URI.create(KC + "/users/" + CAROL)));
            expectRoleLookup("admin");
            keycloak.expect(requestTo(KC + "/users/" + CAROL + "/role-mappings/realm"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(jsonPath("$[0].name").value("admin"))
                    .andExpect(jsonPath("$[0].id").value("role-id-admin"))
                    .andRespond(withNoContent());
            expectCollaborator(CAROL, "new.person@shop.test", true, "admin");

            mockMvc.perform(post(API).contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPathOf("$.id", is(CAROL)))
                    .andExpect(jsonPathOf("$.role", is("admin")))
                    .andExpect(jsonPathOf("$.temporaryPassword").doesNotExist());
            keycloak.verify();
        }

        @Test
        void shouldAnswer409WhenTheEmailIsTaken() throws Exception {
            keycloak.expect(requestTo(KC + "/users"))
                    .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                            .body("{\"errorMessage\":\"User exists with same username\"}"));

            mockMvc.perform(post(API).contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPathOf("$.message", is("User exists with same username")));
        }

        @Test
        void shouldRelayAPasswordRejectedByTheRealmPolicy() throws Exception {
            keycloak.expect(requestTo(KC + "/users"))
                    .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                            .body("{\"error\":\"invalidPasswordNotEmailMessage\","
                                    + "\"error_description\":\"Invalid password: must not be equal to the email.\"}"));

            mockMvc.perform(post(API).contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPathOf("$.message",
                            is("Invalid password: must not be equal to the email.")));
        }

        @Test
        void shouldDeleteTheAccountWhenItsRoleCannotBeGranted() throws Exception {
            keycloak.expect(requestTo(KC + "/users"))
                    .andRespond(withCreatedEntity(URI.create(KC + "/users/" + CAROL)));
            keycloak.expect(requestTo(KC + "/roles/admin")).andRespond(withServerError());
            keycloak.expect(requestTo(KC + "/users/" + CAROL))
                    .andExpect(method(HttpMethod.DELETE))
                    .andRespond(withNoContent());

            mockMvc.perform(post(API).contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isBadGateway());
            keycloak.verify();
        }

        @Test
        void shouldValidateTheRequestBeforeCallingKeycloak() throws Exception {
            mockMvc.perform(post(API).contentType(MediaType.APPLICATION_JSON).content("""
                            {"email":"not-an-email","firstName":"","lastName":"X","temporaryPassword":"short"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPathOf("$.details", containsInAnyOrder(
                            "email must be a valid e-mail address",
                            "firstName must not be blank",
                            "role must be 'collaborator' or 'admin'",
                            "temporaryPassword must be between 8 and 128 characters")));
            keycloak.verify();
        }

        @Test
        void shouldRejectARoleThatIsNotABackOfficeRole() throws Exception {
            mockMvc.perform(post(API).contentType(MediaType.APPLICATION_JSON).content(
                            BODY.replace("\"admin\"", "\"client\"")))
                    .andExpect(status().isBadRequest());
            keycloak.verify();
        }
    }

    @Nested
    class RoleChange {

        @Test
        void shouldPromoteBeforeRemovingThePreviousRole() throws Exception {
            expectCollaborator(BOB, "bob@shop.test", true, "collaborator");
            expectRoleLookup("admin");
            keycloak.expect(requestTo(KC + "/users/" + BOB + "/role-mappings/realm"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(jsonPath("$[0].name").value("admin"))
                    .andRespond(withNoContent());
            expectRoleLookup("collaborator");
            keycloak.expect(requestTo(KC + "/users/" + BOB + "/role-mappings/realm"))
                    .andExpect(method(HttpMethod.DELETE))
                    .andExpect(jsonPath("$[0].name").value("collaborator"))
                    .andRespond(withNoContent());
            expectCollaborator(BOB, "bob@shop.test", true, "admin");

            mockMvc.perform(put(API + "/" + BOB + "/role").header("X-Auth-Subject", ALICE)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"admin\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPathOf("$.role", is("admin")));
            keycloak.verify();
        }

        @Test
        void shouldDoNothingWhenTheRoleIsAlreadyHeld() throws Exception {
            expectCollaborator(BOB, "bob@shop.test", true, "admin");

            mockMvc.perform(put(API + "/" + BOB + "/role").header("X-Auth-Subject", ALICE)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"admin\"}"))
                    .andExpect(status().isOk());
            keycloak.verify();
        }

        @Test
        void shouldRefuseToChangeTheRoleOfTheCaller() throws Exception {
            mockMvc.perform(put(API + "/" + ALICE + "/role").header("X-Auth-Subject", ALICE)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"collaborator\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPathOf("$.message", is("You cannot change your own role")));
            keycloak.verify();
        }
    }

    @Nested
    class Activation {

        @Test
        void shouldDisableTheAccountKeepingItsProfileAndEndItsSessions() throws Exception {
            expectCollaborator(BOB, "bob@shop.test", true, "collaborator");
            expectGet(KC + "/users/" + BOB, """
                    {"id":"%s","username":"bob@shop.test","email":"bob@shop.test","enabled":true,
                     "attributes":{"phone":["0600000000"]}}""".formatted(BOB));
            keycloak.expect(requestTo(KC + "/users/" + BOB))
                    .andExpect(method(HttpMethod.PUT))
                    .andExpect(jsonPath("$.enabled").value(false))
                    .andExpect(jsonPath("$.attributes.phone[0]").value("0600000000"))
                    .andRespond(withNoContent());
            keycloak.expect(requestTo(KC + "/users/" + BOB + "/logout"))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withNoContent());
            expectCollaborator(BOB, "bob@shop.test", false, "collaborator");

            mockMvc.perform(post(API + "/" + BOB + "/disable").header("X-Auth-Subject", ALICE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPathOf("$.enabled", is(false)));
            keycloak.verify();
        }

        @Test
        void shouldRefuseToDisableTheCaller() throws Exception {
            mockMvc.perform(post(API + "/" + ALICE + "/disable").header("X-Auth-Subject", ALICE))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPathOf("$.message",
                            is("You cannot disable your own account")));
            keycloak.verify();
        }

        @Test
        void shouldNotDisableAShopCustomer() throws Exception {
            expectGet(KC + "/users/" + CAROL, user(CAROL, "carol@shop.test", true));
            expectGet(KC + "/users/" + CAROL + "/role-mappings/realm", roles("client"));

            mockMvc.perform(post(API + "/" + CAROL + "/disable").header("X-Auth-Subject", ALICE))
                    .andExpect(status().isNotFound());
            keycloak.verify();
        }

        @Test
        void shouldEnableTheAccountAgain() throws Exception {
            expectCollaborator(BOB, "bob@shop.test", false, "collaborator");
            expectGet(KC + "/users/" + BOB, "{\"id\":\"" + BOB + "\",\"enabled\":false}");
            keycloak.expect(requestTo(KC + "/users/" + BOB))
                    .andExpect(method(HttpMethod.PUT))
                    .andExpect(jsonPath("$.enabled").value(true))
                    .andRespond(withNoContent());
            expectCollaborator(BOB, "bob@shop.test", true, "collaborator");

            mockMvc.perform(post(API + "/" + BOB + "/enable"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPathOf("$.enabled", is(true)));
            keycloak.verify();
        }
    }

    // --- Keycloak side --------------------------------------------------------------------

    private void expectCollaborator(String id, String email, boolean enabled, String role) {
        expectGet(KC + "/users/" + id, user(id, email, enabled));
        expectGet(KC + "/users/" + id + "/role-mappings/realm", roles("default-roles-ecommerce", role));
    }

    private void expectRoleLookup(String role) {
        expectGet(KC + "/roles/" + role, "{\"id\":\"role-id-" + role + "\",\"name\":\"" + role + "\"}");
    }

    private void expectGet(String url, String body) {
        keycloak.expect(requestTo(url)).andExpect(method(HttpMethod.GET)).andRespond(json(body));
    }

    private static ResponseCreator json(String body) {
        return withSuccess(body, MediaType.APPLICATION_JSON);
    }

    private static String user(String id, String email, boolean enabled) {
        return """
                {"id":"%s","username":"%s","email":"%s","firstName":"First","lastName":"Last",
                 "enabled":%s,"createdTimestamp":1793520000000}""".formatted(id, email, email, enabled);
    }

    private static String roles(String... names) {
        return Arrays.stream(names)
                .map(name -> "{\"id\":\"role-id-" + name + "\",\"name\":\"" + name + "\"}")
                .collect(Collectors.joining(",", "[", "]"));
    }

    /** The MockMvc {@code jsonPath}, whose name the Keycloak request matchers already take. */
    private static JsonPathResultMatchers jsonPathOf(String path) {
        return MockMvcResultMatchers.jsonPath(path);
    }

    private static <T> ResultMatcher jsonPathOf(String path, Matcher<T> matcher) {
        return MockMvcResultMatchers.jsonPath(path, matcher);
    }
}
