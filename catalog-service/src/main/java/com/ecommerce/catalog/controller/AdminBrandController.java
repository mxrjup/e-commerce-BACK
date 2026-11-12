package com.ecommerce.catalog.controller;

import com.ecommerce.catalog.dto.BrandRequest;
import com.ecommerce.catalog.dto.BrandResponse;
import com.ecommerce.catalog.service.BrandService;
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
 * Back-office management of brands. The gateway reserves {@code /api/admin/**}
 * to the {@code collaborator} and {@code admin} roles.
 */
@RestController
@RequestMapping("/api/admin/catalog/brands")
@RequiredArgsConstructor
public class AdminBrandController {

    private final BrandService brandService;

    @GetMapping
    public List<BrandResponse> list() {
        return brandService.list();
    }

    @GetMapping("/{id}")
    public BrandResponse get(@PathVariable Long id) {
        return brandService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BrandResponse create(@Valid @RequestBody BrandRequest request) {
        return brandService.create(request);
    }

    @PutMapping("/{id}")
    public BrandResponse update(@PathVariable Long id, @Valid @RequestBody BrandRequest request) {
        return brandService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        brandService.delete(id);
    }
}
