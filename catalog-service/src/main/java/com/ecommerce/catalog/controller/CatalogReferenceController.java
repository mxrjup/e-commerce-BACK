package com.ecommerce.catalog.controller;

import com.ecommerce.catalog.dto.BrandResponse;
import com.ecommerce.catalog.dto.CategoryResponse;
import com.ecommerce.catalog.dto.CategoryTreeNode;
import com.ecommerce.catalog.dto.UniverseResponse;
import com.ecommerce.catalog.service.BrandService;
import com.ecommerce.catalog.service.CategoryService;
import com.ecommerce.catalog.service.UniverseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only access to brands, categories and universes for the storefront.
 * Public through the gateway: browsing the catalogue needs no account.
 * Entries are addressed by slug, the identifier that appears in URLs.
 */
@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class CatalogReferenceController {

    private final BrandService brandService;
    private final CategoryService categoryService;
    private final UniverseService universeService;

    @GetMapping("/brands")
    public List<BrandResponse> brands() {
        return brandService.list();
    }

    @GetMapping("/brands/{slug}")
    public BrandResponse brand(@PathVariable String slug) {
        return brandService.getBySlug(slug);
    }

    @GetMapping("/categories")
    public List<CategoryTreeNode> categories() {
        return categoryService.tree();
    }

    @GetMapping("/categories/{slug}")
    public CategoryResponse category(@PathVariable String slug) {
        return categoryService.getBySlug(slug);
    }

    @GetMapping("/universes")
    public List<UniverseResponse> universes() {
        return universeService.list();
    }

    @GetMapping("/universes/{slug}")
    public UniverseResponse universe(@PathVariable String slug) {
        return universeService.getBySlug(slug);
    }
}
