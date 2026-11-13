package com.ecommerce.catalog.controller;

import com.ecommerce.catalog.dto.PageResponse;
import com.ecommerce.catalog.dto.ProductCardResponse;
import com.ecommerce.catalog.dto.ProductDetailResponse;
import com.ecommerce.catalog.service.ProductCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The products of the storefront (C02). Public through the gateway, like the
 * rest of {@code GET /api/catalog/**}: browsing needs no account.
 */
@RestController
@RequestMapping("/api/catalog/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductCatalogService productCatalogService;

    /** {@code ?category=peripheriques&brand=logitech&universe=gaming&sort=price-asc&page=0&size=20} */
    @GetMapping
    public PageResponse<ProductCardResponse> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String universe,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + ProductCatalogService.DEFAULT_SIZE) int size) {
        return productCatalogService.list(category, brand, universe, sort, page, size);
    }

    @GetMapping("/{slug}")
    public ProductDetailResponse product(@PathVariable String slug) {
        return productCatalogService.get(slug);
    }
}
