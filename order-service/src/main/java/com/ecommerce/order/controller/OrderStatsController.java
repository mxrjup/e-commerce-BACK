package com.ecommerce.order.controller;

import com.ecommerce.order.dto.OrderStatsResponse;
import com.ecommerce.order.dto.OrderStatsResponse.Granularity;
import com.ecommerce.order.dto.OrderStatsResponse.Ranking;
import com.ecommerce.order.service.OrderStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;

/** Back-office authorization is enforced by the API gateway, as for AdminOrderController. */
@RestController
@RequestMapping("/api/admin/orders/stats")
@RequiredArgsConstructor
public class OrderStatsController {
    private final OrderStatsService service;

    @GetMapping
    public OrderStatsResponse stats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "DAY") Granularity granularity,
            @RequestParam(defaultValue = "QUANTITY") Ranking ranking) {
        return service.stats(from, to, granularity, ranking);
    }
}
