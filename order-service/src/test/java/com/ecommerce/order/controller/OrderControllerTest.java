package com.ecommerce.order.controller;

import com.ecommerce.order.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @Test
    @DisplayName("POST /api/orders/webhook returns 400 when Stripe-Signature header is missing")
    void testWebhook_MissingSignature_ReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/orders/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"evt_123\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("POST /api/orders/webhook returns 200 when signature is present and service succeeds")
    void testWebhook_Success_ReturnsOk() throws Exception {
        String payload = "{\"id\":\"evt_123\"}";
        String signature = "t=123456,v1=valid_sig";

        doNothing().when(orderService).handleStripeWebhook(payload, signature);

        mockMvc.perform(post("/api/orders/webhook")
                        .header("Stripe-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        verify(orderService).handleStripeWebhook(payload, signature);
    }

}
