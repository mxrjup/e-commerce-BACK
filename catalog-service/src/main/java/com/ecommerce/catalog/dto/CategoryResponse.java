package com.ecommerce.catalog.dto;

import com.ecommerce.catalog.entity.Category;

public record CategoryResponse(Long id, String name, String slug, String description, Long parentId,
                               int position) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getSlug(),
                category.getDescription(), category.getParentId(), category.getPosition());
    }
}
