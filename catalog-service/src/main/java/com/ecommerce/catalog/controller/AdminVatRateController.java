package com.ecommerce.catalog.controller;

import com.ecommerce.catalog.dto.VatRateRequest;
import com.ecommerce.catalog.dto.VatRateResponse;
import com.ecommerce.catalog.service.VatRateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Back-office management of VAT rates; the storefront has no route to them.
 * The gateway reserves {@code /api/admin/**} to the {@code collaborator} and
 * {@code admin} roles.
 */
@RestController
@RequestMapping("/api/admin/catalog/vat-rates")
@RequiredArgsConstructor
public class AdminVatRateController {

    private final VatRateService vatRateService;

    @GetMapping
    public List<VatRateResponse> list() {
        return vatRateService.list();
    }

    @GetMapping("/{id}")
    public VatRateResponse get(@PathVariable Long id) {
        return vatRateService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VatRateResponse create(@Valid @RequestBody VatRateRequest request) {
        return vatRateService.create(request);
    }

    @PutMapping("/{id}")
    public VatRateResponse update(@PathVariable Long id, @Valid @RequestBody VatRateRequest request) {
        return vatRateService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        vatRateService.delete(id);
    }
}
