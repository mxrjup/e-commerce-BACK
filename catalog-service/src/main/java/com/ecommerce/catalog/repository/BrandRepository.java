package com.ecommerce.catalog.repository;

import com.ecommerce.catalog.entity.Brand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BrandRepository extends JpaRepository<Brand, Long> {

    Optional<Brand> findBySlug(String slug);

    Optional<Brand> findByNameIgnoreCase(String name);
}
