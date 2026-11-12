package com.ecommerce.catalog.controller;

import com.ecommerce.catalog.dto.InternalVariantResponse;
import com.ecommerce.catalog.service.InternalCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Service-to-service reads of variants (C10), for Cart, Order and the MCP.
 * The gateway has no route to {@code /api/internal/**}: only the services,
 * inside the cluster or the Compose network, can call it. That is why the
 * purchase price can appear here and never on the public API.
 */
@RestController
@RequestMapping("/api/internal/catalog/variants")
@RequiredArgsConstructor
public class InternalVariantController {

    private final InternalCatalogService internalCatalogService;

    @GetMapping("/{id}")
    public InternalVariantResponse variant(@PathVariable Long id) {
        return internalCatalogService.variant(id);
    }

    /** {@code ?ids=1,2,3}: a whole cart or order in one call. */
    @GetMapping
    public List<InternalVariantResponse> variants(@RequestParam(required = false) List<Long> ids) {
        return internalCatalogService.variants(ids);
    }
}
