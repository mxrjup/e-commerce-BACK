package com.ecommerce.catalog.dto;

import com.ecommerce.catalog.entity.Brand;

public record BrandResponse(Long id, String name, String slug, String description, String logoUrl) {

    public static BrandResponse from(Brand brand) {
        return new BrandResponse(brand.getId(), brand.getName(), brand.getSlug(),
                brand.getDescription(), brand.getLogoUrl());
    }
}
