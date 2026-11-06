package com.ecommerce.catalog.repository;

import com.ecommerce.catalog.entity.VatRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VatRateRepository extends JpaRepository<VatRate, Long> {

    Optional<VatRate> findByLabelIgnoreCase(String label);

    Optional<VatRate> findByDefaultRateTrue();
}
