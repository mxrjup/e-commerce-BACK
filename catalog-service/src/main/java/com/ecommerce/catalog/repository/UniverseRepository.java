package com.ecommerce.catalog.repository;

import com.ecommerce.catalog.entity.Universe;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UniverseRepository extends JpaRepository<Universe, Long> {

    Optional<Universe> findBySlug(String slug);

    Optional<Universe> findByNameIgnoreCase(String name);
}
