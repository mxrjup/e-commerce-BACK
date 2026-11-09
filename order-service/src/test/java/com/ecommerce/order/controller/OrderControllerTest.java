package com.ecommerce.order.controller;

import com.ecommerce.order.dto.SavePaymentMethodRequest;
import com.ecommerce.order.dto.SavedPaymentMethodResponse;
import com.ecommerce.order.service.OrderService;
import com.ecommerce.order.service.StripePaymentMethodService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @MockBean
    private StripePaymentMethodService stripePaymentMethodService;

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

    @Test
    @DisplayName("GET /api/orders/payment-methods returns saved payment methods for the authenticated user")
    void testGetSavedPaymentMethods_ReturnsList() throws Exception {
        SavedPaymentMethodResponse response = new SavedPaymentMethodResponse();
        response.setId(12L);
        response.setStripePaymentMethodId("pm_123");
        response.setBrand("visa");
        response.setLast4("4242");
        response.setDefaultPaymentMethod(true);

        when(stripePaymentMethodService.getSavedPaymentMethods(42L)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/orders/payment-methods")
                        .header("X-User-Id", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stripePaymentMethodId").value("pm_123"))
                .andExpect(jsonPath("$[0].brand").value("visa"))
                .andExpect(jsonPath("$[0].defaultPaymentMethod").value(true));
    }

    @Test
    @DisplayName("POST /api/orders/payment-methods saves a new Stripe payment method and returns 201")
    void testSavePaymentMethod_ReturnsCreated() throws Exception {
        when(stripePaymentMethodService.savePaymentMethod(42L, "pm_789")).thenReturn("pm_789");

        mockMvc.perform(post("/api/orders/payment-methods")
                        .header("X-User-Id", "42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethodId\":\"pm_789\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stripePaymentMethodId").value("pm_789"));

        verify(stripePaymentMethodService).savePaymentMethod(42L, "pm_789");
    }

    @Test
    @DisplayName("POST /api/orders/payment-methods/default sets the selected payment method as default")
    void testSetDefaultPaymentMethod_ReturnsOk() throws Exception {
        mockMvc.perform(post("/api/orders/payment-methods/default")
                        .header("X-User-Id", "42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethodId\":\"pm_123\"}"))
                .andExpect(status().isOk());

        verify(stripePaymentMethodService).setDefaultPaymentMethod(42L, "pm_123");
    }
}
