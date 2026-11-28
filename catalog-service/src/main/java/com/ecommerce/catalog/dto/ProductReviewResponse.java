package com.ecommerce.catalog.dto;

import com.ecommerce.catalog.entity.ProductReview;

import java.time.Instant;
import java.util.List;

public record ProductReviewResponse(Long id, int rating, String comment, Instant createdAt, Instant updatedAt) {

    public static ProductReviewResponse from(ProductReview review) {
        return new ProductReviewResponse(review.getId(), review.getRating(), review.getComment(),
                review.getCreatedAt(), review.getUpdatedAt());
    }

    public record ReviewPage(List<ProductReviewResponse> reviews, Double averageRating, long totalReviews,
                             int page, int size, boolean hasNext) {
    }
}
