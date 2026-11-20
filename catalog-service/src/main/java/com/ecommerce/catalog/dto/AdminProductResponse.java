package com.ecommerce.catalog.dto;

import com.ecommerce.catalog.entity.Product;
import com.ecommerce.catalog.entity.ProductStatus;
import com.ecommerce.catalog.entity.Universe;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * A whole product sheet for the back-office form (C16): the ids the form
 * sends back, the names it displays, and every variant with its exact stock.
 */
public record AdminProductResponse(
        Long id,
        String title,
        String slug,
        String description,
        ProductStatus status,
        Reference brand,
        Reference category,
        VatRateView vatRate,
        List<Reference> universes,
        List<AdminProductRequest.Specification> specifications,
        List<AdminVariantResponse> variants,
        Instant createdAt,
        Instant updatedAt) {

    public record Reference(Long id, String name) {
    }

    public record VatRateView(Long id, String label, BigDecimal rate) {
    }

    public static AdminProductResponse from(Product product) {
        return new AdminProductResponse(
                product.getId(),
                product.getTitle(),
                product.getSlug(),
                product.getDescription(),
                product.getStatus(),
                new Reference(product.getBrand().getId(), product.getBrand().getName()),
                new Reference(product.getCategory().getId(), product.getCategory().getName()),
                new VatRateView(product.getVatRate().getId(), product.getVatRate().getLabel(),
                        product.getVatRate().getRate()),
                product.getUniverses().stream()
                        .sorted(Comparator.comparing(Universe::getPosition).thenComparing(Universe::getName))
                        .map(universe -> new Reference(universe.getId(), universe.getName()))
                        .toList(),
                product.getSpecifications().stream()
                        .map(spec -> new AdminProductRequest.Specification(spec.getKey(), spec.getValue()))
                        .toList(),
                product.getVariants().stream().map(AdminVariantResponse::from).toList(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }
}
