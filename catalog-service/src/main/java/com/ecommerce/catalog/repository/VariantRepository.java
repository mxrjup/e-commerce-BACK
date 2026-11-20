package com.ecommerce.catalog.repository;

import com.ecommerce.catalog.entity.Variant;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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

    @EntityGraph(attributePaths = {"product", "product.vatRate", "values", "values.attribute"})
    @Query("select distinct v from Product source"
            + " join source.accessories accessory"
            + " join accessory.variants v"
            + " where source.id in :sourceProductIds"
            + " order by v.product.id, v.id")
    List<Variant> findAccessoryVariantsBySourceProductIds(@Param("sourceProductIds") Collection<Long> sourceProductIds);

    /**
     * The back-office inventory (C07): every variant, whatever the status of
     * its product, only the alerts when asked. {@code pattern} is a lowercase
     * LIKE pattern on the product title or the SKU, {@code %} for all, with
     * {@code !} as escape character.
     */
    @EntityGraph(attributePaths = "product")
    @Query(value = "select v from Variant v join v.product p"
            + " where (lower(p.title) like :pattern escape '!' or lower(v.sku) like :pattern escape '!')"
            + " and (:lowStockOnly = false or v.stock <= v.alertThreshold)"
            + " order by lower(p.title), v.id",
            countQuery = "select count(v) from Variant v join v.product p"
                    + " where (lower(p.title) like :pattern escape '!' or lower(v.sku) like :pattern escape '!')"
                    + " and (:lowStockOnly = false or v.stock <= v.alertThreshold)")
    Page<Variant> findInventory(@Param("pattern") String pattern, @Param("lowStockOnly") boolean lowStockOnly,
                                Pageable pageable);

    /**
     * Adds {@code delta} to the stock in one statement, only if the result
     * stays at zero or above: two concurrent requests cannot both take the
     * last unit, the database serialises them on the row. Returns 1 when
     * applied, 0 when the stock was too low (or the variant does not exist).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Variant v set v.stock = v.stock + :delta, v.updatedAt = :now"
            + " where v.id = :id and v.stock + :delta >= 0")
    int addToStock(@Param("id") Long id, @Param("delta") int delta, @Param("now") Instant now);

    /**
     * The stock of a variant, with its row locked until the end of the
     * transaction: no other order can take units between this read and the
     * update that follows it.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v.stock from Variant v where v.id = :id")
    Optional<Integer> lockStock(@Param("id") Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Variant v set v.alertThreshold = :threshold, v.updatedAt = :now where v.id = :id")
    int setAlertThreshold(@Param("id") Long id, @Param("threshold") int threshold, @Param("now") Instant now);
}
