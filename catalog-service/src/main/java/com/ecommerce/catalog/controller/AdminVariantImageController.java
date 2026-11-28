package com.ecommerce.catalog.controller;

import com.ecommerce.catalog.dto.AdminVariantResponse;
import com.ecommerce.catalog.dto.ImageOrderRequest;
import com.ecommerce.catalog.service.VariantImageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * The photos of a variant from the back-office (C06, screen C18). Each call
 * answers with the variant and its images in their new order.
 */
@RestController
@RequestMapping("/api/admin/catalog/variants/{variantId}/images")
@RequiredArgsConstructor
public class AdminVariantImageController {

    private final VariantImageService imageService;

    /** A multipart form with one {@code file}: JPEG, PNG or WebP, 5 MB at most. It goes last. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public AdminVariantResponse upload(@PathVariable Long variantId, @RequestParam("file") MultipartFile file) {
        return imageService.upload(variantId, file);
    }

    @DeleteMapping("/{imageId}")
    public AdminVariantResponse delete(@PathVariable Long variantId, @PathVariable Long imageId) {
        return imageService.delete(variantId, imageId);
    }

    @PutMapping("/order")
    public AdminVariantResponse reorder(@PathVariable Long variantId, @Valid @RequestBody ImageOrderRequest request) {
        return imageService.reorder(variantId, request.imageIds());
    }
}
