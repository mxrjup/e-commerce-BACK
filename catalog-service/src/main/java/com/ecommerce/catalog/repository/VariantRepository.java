package com.ecommerce.catalog.repository;

import com.ecommerce.catalog.entity.Variant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VariantRepository extends JpaRepository<Variant, Long> {

    Optional<Variant> findBySku(String sku);
}
