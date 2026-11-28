package com.ecommerce.catalog.service;

import com.ecommerce.catalog.dto.ProductReviewResponse;
import com.ecommerce.catalog.dto.ProductReviewResponse.ReviewPage;
import com.ecommerce.catalog.entity.ProductReview;
import com.ecommerce.catalog.repository.ProductRepository;
import com.ecommerce.catalog.repository.ProductReviewRepository;
import com.ecommerce.common.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductReviewService {

    private final ProductRepository products;
    private final ProductReviewRepository reviews;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ReviewPage list(String slug, int page, int size) {
        long offset = (long) page * size;
        if (page < 0 || size < 1 || size > ProductCatalogService.MAX_SIZE || offset > Integer.MAX_VALUE) {
            throw new BadRequestException("Invalid review pagination");
        }
        Long productId = ProductCatalogService.requireStorefrontProduct(slug, products.findBySlug(slug)).getId();
        ProductReviewRepository.Statistics stats = reviews.statistics(productId);
        List<ProductReviewResponse> content = offset >= stats.getTotalReviews() ? List.of()
                : reviews.findByProductIdOrderByCreatedAtDescIdDesc(productId, PageRequest.of(page, size))
                        .stream().map(ProductReviewResponse::from).toList();
        return new ReviewPage(content, stats.getAverageRating(), stats.getTotalReviews(),
                page, size, offset + size < stats.getTotalReviews());
    }

    @Transactional
    public ProductReviewResponse save(String slug, String subject, int rating, String comment) {
        // Serialize competing writes before checking whether this author already has a review.
        Long productId = ProductCatalogService.requireStorefrontProduct(slug, products.findForUpdateBySlug(slug))
                .getId();
        ProductReview review = reviews.findByProductIdAndAuthorSubject(productId, subject)
                .orElseGet(() -> new ProductReview(productId, subject));
        review.revise(rating, comment);
        return ProductReviewResponse.from(reviews.saveAndFlush(review));
    }
}
