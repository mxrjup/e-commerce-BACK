package com.ecommerce.order.controller;

import com.ecommerce.order.service.OrderStatsService;
import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.order.dto.OrderStatsResponse.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderStatsController.class)
@org.springframework.context.annotation.Import(com.ecommerce.common.web.GlobalExceptionHandler.class)
class OrderStatsControllerTest {
    @Autowired MockMvc mvc;
    @MockBean OrderStatsService service;
    private static final String PATH = "/api/admin/orders/stats";
    private static final String FROM = "2026-01-01T00:00:00Z";
    private static final String TO = "2026-02-01T00:00:00Z";

    @Test
    void routesStatsAndUsesDefaultOptions() throws Exception {
        mvc.perform(get(PATH).param("from", FROM).param("to", TO)).andExpect(status().isOk());
        verify(service).stats(Instant.parse(FROM), Instant.parse(TO), Granularity.DAY, Ranking.QUANTITY);
    }

    @Test
    void rejectsMissingMalformedAndUnsupportedParameters() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isBadRequest());
        mvc.perform(get(PATH).param("from", "yesterday").param("to", TO)).andExpect(status().isBadRequest());
        mvc.perform(get(PATH).param("from", FROM).param("to", TO).param("granularity", "YEAR"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(PATH).param("from", FROM).param("to", TO).param("ranking", "INVALID"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void mapsInvalidPeriodToBadRequest() throws Exception {
        when(service.stats(any(), any(), any(), any())).thenThrow(new BadRequestException("from must precede to"));
        mvc.perform(get(PATH).param("from", TO).param("to", FROM)).andExpect(status().isBadRequest());
    }
}
