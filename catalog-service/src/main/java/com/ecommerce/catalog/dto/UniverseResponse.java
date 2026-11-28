package com.ecommerce.catalog.dto;

import com.ecommerce.catalog.entity.Universe;

public record UniverseResponse(Long id, String name, String slug, String description, String imageUrl,
                               int position) {

    public static UniverseResponse from(Universe universe) {
        return new UniverseResponse(universe.getId(), universe.getName(), universe.getSlug(),
                universe.getDescription(), universe.getImageUrl(), universe.getPosition());
    }
}
