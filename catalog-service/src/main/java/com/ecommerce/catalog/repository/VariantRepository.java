package com.ecommerce.catalog.repository;

import com.ecommerce.catalog.entity.Variant;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VariantRepository extends JpaRepository<Variant, Long> {

    Optional<Variant> findBySku(String sku);

    /**
     * The variants with their product, rate and values in one query: without
     * the graph, each variant would load them one by one. The images stay
     * out: joining a second collection would repeat each image once per
     * value. They come in one extra query, batched on {@link Variant}.
     */
    @EntityGraph(attributePaths = {"product", "product.vatRate", "values", "values.attribute"})
    List<Variant> findWithDetailsByIdIn(Collection<Long> ids);
}
