package com.ecommerce.catalog.controller;

import com.ecommerce.catalog.dto.AlertThresholdRequest;
import com.ecommerce.catalog.dto.InventoryItemResponse;
import com.ecommerce.catalog.dto.PageResponse;
import com.ecommerce.catalog.dto.StockAdjustmentRequest;
import com.ecommerce.catalog.service.StockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The stock in the back-office (C07, screen C20). The gateway reserves
 * {@code /api/admin/**} to the {@code collaborator} and {@code admin} roles.
 */
@RestController
@RequestMapping("/api/admin/catalog")
@RequiredArgsConstructor
public class AdminStockController {

    private final StockService stockService;

    /** {@code ?search=g915&lowStock=true&page=0&size=20} */
    @GetMapping("/inventory")
    public PageResponse<InventoryItemResponse> inventory(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "false") boolean lowStock,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return stockService.inventory(search, lowStock, page, size);
    }

    /** {@code {"delta": 10}} for a delivery, {@code {"delta": -1}} for a broken unit; 409 below zero. */
    @PostMapping("/variants/{id}/stock-adjustments")
    public InventoryItemResponse adjust(@PathVariable Long id, @Valid @RequestBody StockAdjustmentRequest request) {
        return stockService.adjust(id, request.delta());
    }

    @PutMapping("/variants/{id}/alert-threshold")
    public InventoryItemResponse alertThreshold(@PathVariable Long id,
                                                @Valid @RequestBody AlertThresholdRequest request) {
        return stockService.setAlertThreshold(id, request.alertThreshold());
    }
}
