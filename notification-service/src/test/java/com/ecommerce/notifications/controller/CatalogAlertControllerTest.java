package com.ecommerce.notifications.controller;

import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.notifications.dto.CatalogAlertResponseDto;
import com.ecommerce.notifications.entity.CatalogAlertType;
import com.ecommerce.notifications.service.CatalogAlertService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import com.ecommerce.common.web.GlobalExceptionHandler;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebMvcTest(CatalogAlertController.class)
@Import(GlobalExceptionHandler.class)
class CatalogAlertControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CatalogAlertService catalogAlertService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldCreatePriceDropAlert() throws Exception {
        CatalogAlertResponseDto response = CatalogAlertResponseDto.builder()
                .id(1L)
                .productId(10L)
                .variantId(100L)
                .alertType(CatalogAlertType.PRICE_DROP)
                .targetPriceExclTax(new BigDecimal("29.90"))
                .active(true)
                .createdAt(Instant.now())
                .build();

        when(catalogAlertService.createAlert(eq("user-1"), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/notifications/alerts")
                        .with(jwt().jwt(j -> j.subject("user-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": 10,
                                  "variantId": 100,
                                  "alertType": "PRICE_DROP",
                                  "targetPriceExclTax": 29.90
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.variantId").value(100))
                .andExpect(jsonPath("$.alertType").value("PRICE_DROP"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void shouldReturn409WhenDuplicateActiveAlert() throws Exception {
        when(catalogAlertService.createAlert(eq("user-1"), any()))
                .thenThrow(new ConflictException("An active alert already exists"));

        mockMvc.perform(post("/api/v1/notifications/alerts")
                        .with(jwt().jwt(j -> j.subject("user-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "variantId": 100,
                                  "alertType": "BACK_IN_STOCK"
                                }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldListUpdateAndDeleteAlerts() throws Exception {
        CatalogAlertResponseDto dto = CatalogAlertResponseDto.builder()
                .id(2L)
                .variantId(200L)
                .alertType(CatalogAlertType.BACK_IN_STOCK)
                .active(true)
                .createdAt(Instant.now())
                .build();

        when(catalogAlertService.getUserAlerts("user-1", true)).thenReturn(List.of(dto));
        when(catalogAlertService.updateAlert(eq(2L), eq("user-1"), any())).thenReturn(dto);
        doNothing().when(catalogAlertService).deleteAlert(2L, "user-1");

        mockMvc.perform(get("/api/v1/notifications/alerts?activeOnly=true")
                        .with(jwt().jwt(j -> j.subject("user-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2));

        mockMvc.perform(patch("/api/v1/notifications/alerts/2")
                        .with(jwt().jwt(j -> j.subject("user-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\": true}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/notifications/alerts/2")
                        .with(jwt().jwt(j -> j.subject("user-1"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldReturn404WhenAlertNotOwned() throws Exception {
        when(catalogAlertService.getUserAlert(99L, "user-1"))
                .thenThrow(new ResourceNotFoundException("Catalog alert not found: 99"));

        mockMvc.perform(get("/api/v1/notifications/alerts/99")
                        .with(jwt().jwt(j -> j.subject("user-1"))))
                .andExpect(status().isNotFound());
    }
}