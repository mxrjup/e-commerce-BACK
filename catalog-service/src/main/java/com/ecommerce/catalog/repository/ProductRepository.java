package com.ecommerce.catalog.repository;

import com.ecommerce.catalog.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    Optional<Product> findBySlug(String slug);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.slug = :slug")
    Optional<Product> findForUpdateBySlug(@Param("slug") String slug);

    /**
     * The back-office product list (C04), with the brand and category of each
     * row in the same query. The variants come in one extra query, batched
     * on {@link Product}.
     */
    @Override
    @EntityGraph(attributePaths = {"brand", "category"})
    Page<Product> findAll(Specification<Product> specification, Pageable pageable);

    /**
     * A product with its row locked until the end of the transaction. Every
     * write on its variants or its status takes it first, so they run one
     * after the other: two identical variants cannot both pass the duplicate
     * check, and a product cannot be published while its last variant is
     * being deleted.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findForUpdateById(@Param("id") Long id);

    /** The specification keys used in a category, most used first: suggestions for the product form (C16). */
    @Query("select s.key from ProductSpecification s where s.product.category.id = :categoryId"
            + " group by s.key order by count(s) desc, s.key")
    List<String> findSpecificationKeys(@Param("categoryId") Long categoryId, Pageable pageable);
}
