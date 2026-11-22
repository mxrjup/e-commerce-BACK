package com.ecommerce.order.controller;

import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalOrderController.class)
class InternalOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @Test
    void shouldListInternalOrders() throws Exception {
        OrderResponse order = OrderResponse.builder()
                .id(1L)
                .orderNumber("CMD-2026-000001")
                .userId(42L)
                .status("DELIVERED")
                .createdAt(Instant.parse("2026-11-12T10:00:00Z"))
                .items(List.of())
                .build();
        when(orderService.getOrdersForAdmin(42L, null, null, null)).thenReturn(List.of(order));

        mockMvc.perform(get("/api/internal/orders").param("userId", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].orderNumber").value("CMD-2026-000001"));

        verify(orderService).getOrdersForAdmin(42L, null, null, null);
    }

    @Test
    void shouldGetInternalOrderForMcpWithoutOwnerFilter() throws Exception {
        when(orderService.getOrderForInternal(1L, null)).thenReturn(OrderResponse.builder()
                .id(1L)
                .orderNumber("CMD-2026-000001")
                .userId(42L)
                .status("DELIVERED")
                .createdAt(Instant.parse("2026-11-12T10:00:00Z"))
                .items(List.of())
                .build());

        mockMvc.perform(get("/api/internal/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(42));

        verify(orderService).getOrderForInternal(1L, null);
    }

    @Test
    void shouldVerifyOwnerWhenUserIdIsProvided() throws Exception {
        when(orderService.getOrderForInternal(1L, 42L)).thenReturn(OrderResponse.builder()
                .id(1L)
                .orderNumber("CMD-2026-000001")
                .userId(42L)
                .createdAt(Instant.parse("2026-11-12T10:00:00Z"))
                .items(List.of())
                .build());

        mockMvc.perform(get("/api/internal/orders/1").param("userId", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdAt").exists());

        verify(orderService).getOrderForInternal(1L, 42L);
    }

    @Test
    void shouldGetInternalOrderByNumberAndOwner() throws Exception {
        when(orderService.getOrderByNumberForInternal("CMD-2026-000001", 42L)).thenReturn(
                OrderResponse.builder()
                        .id(1L)
                        .orderNumber("CMD-2026-000001")
                        .userId(42L)
                        .createdAt(Instant.parse("2026-11-12T10:00:00Z"))
                        .items(List.of())
                        .build());

        mockMvc.perform(get("/api/internal/orders/by-number/CMD-2026-000001")
                        .param("userId", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value("CMD-2026-000001"));

        verify(orderService).getOrderByNumberForInternal("CMD-2026-000001", 42L);
    }
}
