package com.ecommerce.catalog.repository;

import com.ecommerce.catalog.entity.VatRate;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface VatRateRepository extends JpaRepository<VatRate, Long> {

    Optional<VatRate> findByLabelIgnoreCase(String label);

    Optional<VatRate> findByDefaultRateTrue();

    /**
     * Locks every rate until the end of the transaction. Taken before any
     * write, it makes two concurrent writes run one after the other, so they
     * cannot both mark a different rate as the default. The table is never
     * empty (the default rate cannot be deleted), so there is always a row to
     * lock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from VatRate v")
    List<VatRate> lockAll();
}
