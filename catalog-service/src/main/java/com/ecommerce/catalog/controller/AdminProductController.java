package com.ecommerce.catalog.controller;

import com.ecommerce.catalog.dto.AdminProductRequest;
import com.ecommerce.catalog.dto.AdminProductResponse;
import com.ecommerce.catalog.dto.AdminProductSummary;
import com.ecommerce.catalog.dto.PageResponse;
import com.ecommerce.catalog.dto.ProductStatusRequest;
import com.ecommerce.catalog.entity.ProductStatus;
import com.ecommerce.catalog.service.AdminProductService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Back-office management of the products (C04), every status included. The
 * gateway reserves {@code /api/admin/**} to the {@code collaborator} and
 * {@code admin} roles.
 */
@RestController
@RequestMapping("/api/admin/catalog")
@RequiredArgsConstructor
public class AdminProductController {

    private final AdminProductService productService;

    /** The product list of the back-office (C15). */
    @GetMapping("/products")
    public PageResponse<AdminProductSummary> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return productService.list(search, status, brandId, categoryId, sort, page, size);
    }

    @GetMapping("/products/{id}")
    public AdminProductResponse get(@PathVariable Long id) {
        return productService.get(id);
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminProductResponse create(@Valid @RequestBody AdminProductRequest request) {
        return productService.create(request);
    }

    @PutMapping("/products/{id}")
    public AdminProductResponse update(@PathVariable Long id, @Valid @RequestBody AdminProductRequest request) {
        return productService.update(id, request);
    }

    @PutMapping("/products/{id}/status")
    public AdminProductResponse changeStatus(@PathVariable Long id, @Valid @RequestBody ProductStatusRequest request) {
        return productService.changeStatus(id, request.status());
    }

    @DeleteMapping("/products/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        productService.delete(id);
    }

    /** The specification keys used in a category, most used first: suggestions for the form (C16). */
    @GetMapping("/specification-keys")
    public List<String> specificationKeys(@RequestParam Long categoryId) {
        return productService.specificationKeys(categoryId);
    }
}
