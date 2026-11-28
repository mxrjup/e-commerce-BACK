package com.ecommerce.catalog.repository;

import com.ecommerce.catalog.entity.ProductReview;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {

    Optional<ProductReview> findByProductIdAndAuthorSubject(Long productId, String authorSubject);

    List<ProductReview> findByProductIdOrderByCreatedAtDescIdDesc(Long productId, Pageable pageable);

    @Query("""
            select avg(r.rating) as averageRating, count(r) as totalReviews
            from ProductReview r where r.productId = :productId
            """)
    Statistics statistics(Long productId);

    interface Statistics {
        Double getAverageRating();
        long getTotalReviews();
    }
}
