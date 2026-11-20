package com.ecommerce.catalog.controller;

import com.ecommerce.catalog.dto.AdminVariantRequest;
import com.ecommerce.catalog.dto.AdminVariantResponse;
import com.ecommerce.catalog.service.AdminVariantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Back-office management of the variants (C04). A variant is read with its
 * product ({@code GET /products/{id}}); its stock has its own endpoints (C07).
 */
@RestController
@RequestMapping("/api/admin/catalog")
@RequiredArgsConstructor
public class AdminVariantController {

    private final AdminVariantService variantService;

    @PostMapping("/products/{productId}/variants")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminVariantResponse create(@PathVariable Long productId, @Valid @RequestBody AdminVariantRequest request) {
        return variantService.create(productId, request);
    }

    @PutMapping("/variants/{id}")
    public AdminVariantResponse update(@PathVariable Long id, @Valid @RequestBody AdminVariantRequest request) {
        return variantService.update(id, request);
    }

    @DeleteMapping("/variants/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        variantService.delete(id);
    }
}
