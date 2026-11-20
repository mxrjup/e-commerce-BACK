package com.ecommerce.support;

import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.support.client.OrderClient;
import com.ecommerce.support.client.OrderClient.OrderSummary;
import com.ecommerce.support.client.UserIdentityClient;
import com.ecommerce.support.entity.SupportTicket;
import com.ecommerce.support.entity.TicketStatus;
import com.ecommerce.support.repository.SupportTicketRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local-h2")
class SupportTicketIntegrationTest {

    private static final String CLIENT_A = "sub-client-a";
    private static final String CLIENT_B = "sub-client-b";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SupportTicketRepository ticketRepository;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private UserIdentityClient userIdentityClient;

    @MockBean
    private OrderClient orderClient;

    @BeforeEach
    void setUp() {
        ticketRepository.deleteAll();
        when(userIdentityClient.resolveUserIdBySubject(CLIENT_A)).thenReturn(Optional.of(42L));
        when(userIdentityClient.resolveUserIdBySubject(CLIENT_B)).thenReturn(Optional.of(99L));

        when(orderClient.getCustomerOrderById(42L, 101L)).thenReturn(
                new OrderSummary(101L, "CMD-2026-000101", "DELIVERED", Instant.now().minus(3, ChronoUnit.DAYS))
        );
        when(orderClient.getCustomerOrderByNumber(42L, "CMD-2026-000102")).thenReturn(
                new OrderSummary(102L, "CMD-2026-000102", "SHIPPED", Instant.now().minus(13, ChronoUnit.DAYS))
        );
        when(orderClient.getCustomerOrderById(42L, 202L)).thenThrow(
                new ResourceNotFoundException("Commande introuvable : 202")
        );
        when(orderClient.getCustomerOrderById(42L, 303L)).thenReturn(
                new OrderSummary(303L, "CMD-2026-000303", "DELIVERED", Instant.now().minus(15, ChronoUnit.DAYS))
        );
    }

    @Test
    void shouldCreateTicketListTicketsAndAddCustomerMessages() throws Exception {
        String createPayload = """
                {
                  "subject": "Colis reçu endommagé",
                  "message": "Le carton était ouvert à la livraison.",
                  "category": "DELIVERY",
                  "orderId": 101,
                  "orderNumber": "CMD-2026-000101"
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/v1/support/tickets")
                        .with(jwt().jwt(j -> j.subject(CLIENT_A)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.subject").value("Colis reçu endommagé"))
                .andExpect(jsonPath("$.category").value("DELIVERY"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.orderId").value(101))
                .andExpect(jsonPath("$.orderNumber").value("CMD-2026-000101"))
                .andExpect(jsonPath("$.messages.length()").value(1))
                .andExpect(jsonPath("$.messages[0].authorRole").value("CUSTOMER"))
                .andExpect(jsonPath("$.messages[0].content").value("Le carton était ouvert à la livraison."))
                .andReturn();

        JsonNode createdJson = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long ticketId = createdJson.get("id").asLong();

        SupportTicket persisted = ticketRepository.findById(ticketId).orElseThrow();
        assertThat(persisted.getUserId()).isEqualTo(42L);

        String messagePayload = """
                {
                  "content": "Je joins les photos demandées par email."
                }
                """;

        mockMvc.perform(post("/api/v1/support/tickets/{id}/messages", ticketId)
                        .with(jwt().jwt(j -> j.subject(CLIENT_A)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(messagePayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorRole").value("CUSTOMER"))
                .andExpect(jsonPath("$.content").value("Je joins les photos demandées par email."));

        mockMvc.perform(get("/api/v1/support/tickets")
                        .with(jwt().jwt(j -> j.subject(CLIENT_A))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(ticketId))
                .andExpect(jsonPath("$[0].messageCount").value(2));

        mockMvc.perform(get("/api/v1/support/tickets")
                        .with(jwt().jwt(j -> j.subject(CLIENT_B))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/v1/support/tickets/{id}", ticketId)
                        .with(jwt().jwt(j -> j.subject(CLIENT_B))))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldCreateTicketUsingOrderNumberOnlyWithin14Days() throws Exception {
        String payload = """
                {
                  "subject": "Demande de retour",
                  "message": "Je souhaite exercer mon droit de rétractation.",
                  "category": "RETURN_REFUND",
                  "orderNumber": "CMD-2026-000102"
                }
                """;

        mockMvc.perform(post("/api/v1/support/tickets")
                        .with(jwt().jwt(j -> j.subject(CLIENT_A)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(102))
                .andExpect(jsonPath("$.orderNumber").value("CMD-2026-000102"));
    }

    @Test
    void shouldRejectTicketWhenOrderBelongsToAnotherUser() throws Exception {
        String payload = """
                {
                  "subject": "Problème commande",
                  "message": "Tentative sur la commande d'un autre client.",
                  "category": "ORDER",
                  "orderId": 202
                }
                """;

        mockMvc.perform(post("/api/v1/support/tickets")
                        .with(jwt().jwt(j -> j.subject(CLIENT_A)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isNotFound());

        assertThat(ticketRepository.count()).isZero();
    }

    @Test
    void shouldRejectTicketWhenOrderExceeds14DaysWindow() throws Exception {
        String payload = """
                {
                  "subject": "Retour hors délai",
                  "message": "Commande passée il y a plus de 14 jours.",
                  "category": "RETURN_REFUND",
                  "orderId": 303
                }
                """;

        mockMvc.perform(post("/api/v1/support/tickets")
                        .with(jwt().jwt(j -> j.subject(CLIENT_A)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict());

        assertThat(ticketRepository.count()).isZero();
    }

    @Test
    void shouldRejectOrderCategoryWithoutOrderReference() throws Exception {
        String payload = """
                {
                  "subject": "Question livraison",
                  "message": "Où est mon colis ?",
                  "category": "DELIVERY"
                }
                """;

        mockMvc.perform(post("/api/v1/support/tickets")
                        .with(jwt().jwt(j -> j.subject(CLIENT_A)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectMessageOnClosedTicket() throws Exception {
        SupportTicket closedTicket = ticketRepository.save(
                SupportTicket.builder()
                        .userSubject(CLIENT_A)
                        .userId(42L)
                        .subject("Demande clôturée")
                        .status(TicketStatus.CLOSED)
                        .build()
        );

        mockMvc.perform(post("/api/v1/support/tickets/{id}/messages", closedTicket.getId())
                        .with(jwt().jwt(j -> j.subject(CLIENT_A)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"Toujours d'actualité ?\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldRejectUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/v1/support/tickets"))
                .andExpect(status().isUnauthorized());
    }
}