package com.ecommerce.catalog.controller;

import com.ecommerce.catalog.dto.ProductReviewResponse;
import com.ecommerce.catalog.dto.ProductReviewResponse.ReviewPage;
import com.ecommerce.catalog.service.ProductCatalogService;
import com.ecommerce.catalog.service.ProductReviewService;
import com.ecommerce.common.exception.BadRequestException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/catalog/products/{slug}/reviews")
@RequiredArgsConstructor
public class ProductReviewController {

    private final ProductReviewService reviews;

    @GetMapping
    public ReviewPage list(@PathVariable String slug,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "" + ProductCatalogService.DEFAULT_SIZE) int size) {
        return reviews.list(slug, page, size);
    }

    @PostMapping
    public ProductReviewResponse save(@PathVariable String slug,
                                      @RequestHeader(name = "X-Auth-Subject", required = false) String subject,
                                      @RequestBody JsonNode body) {
        if (subject == null || subject.isBlank() || subject.length() > 255) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        JsonNode rating = body.path("rating");
        if (!rating.isIntegralNumber() || !rating.canConvertToInt() || rating.intValue() < 1 || rating.intValue() > 5) {
            throw new BadRequestException("rating must be an integer between 1 and 5");
        }
        String comment = body.path("comment").textValue();
        if (comment == null || comment.isBlank() || comment.length() > 2000 || comment.indexOf(0) >= 0) {
            throw new BadRequestException("comment must be a non-blank string of at most 2000 characters");
        }
        return reviews.save(slug, subject, rating.intValue(), comment.strip());
    }
}
