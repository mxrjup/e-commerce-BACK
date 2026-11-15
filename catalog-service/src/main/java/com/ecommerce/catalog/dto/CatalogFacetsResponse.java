package com.ecommerce.catalog.dto;

import java.math.BigDecimal;
import java.util.List;

/** Counts are distinct products among all matching results, before pagination. */
public record CatalogFacetsResponse(
        List<ReferenceFacet> categories, List<ReferenceFacet> brands,
        List<ReferenceFacet> universes, PriceRange price,
        List<ValueFacet> attributes, List<ValueFacet> specifications) {
    public record ReferenceFacet(String slug, String name, long count) { }
    public record ValueFacet(String name, String value, long count) { }
    public record PriceRange(BigDecimal min, BigDecimal max) { }
}
