package com.ecommerce.catalog.service;

import com.ecommerce.catalog.dto.AdminVariantRequest;
import com.ecommerce.catalog.dto.AdminVariantResponse;
import com.ecommerce.catalog.entity.AttributeValue;
import com.ecommerce.catalog.entity.Product;
import com.ecommerce.catalog.entity.ProductStatus;
import com.ecommerce.catalog.entity.Variant;
import com.ecommerce.catalog.repository.AttributeValueRepository;
import com.ecommerce.catalog.repository.ProductRepository;
import com.ecommerce.catalog.repository.VariantRepository;
import com.ecommerce.catalog.support.Designations;
import com.ecommerce.catalog.support.Persistence;
import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The variants of a product (C04, screen C17): what is actually sold.
 *
 * <p>Every write locks the product first, so two writes on the same product
 * run one after the other: two identical variants cannot both pass the
 * duplicate check, and the last variant cannot disappear while the product
 * is being published.
 */
@Service
@RequiredArgsConstructor
public class AdminVariantService {

    private final VariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final AttributeValueRepository attributeValueRepository;

    @Transactional
    public AdminVariantResponse create(Long productId, AdminVariantRequest request) {
        Product product = productRepository.findForUpdateById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product " + productId + " not found"));
        Variant variant = new Variant();
        apply(product, variant, request);
        variant.setStock(request.initialStock() == null ? 0 : request.initialStock());
        variant.setAlertThreshold(request.alertThreshold() == null ? 0 : request.alertThreshold());
        product.addVariant(variant);
        return AdminVariantResponse.from(Persistence.orConflict(() -> variantRepository.saveAndFlush(variant),
                "A variant with SKU '" + variant.getSku() + "' already exists"));
    }

    /** Everything but the stock and the alert threshold, which have their own endpoints (C07). */
    @Transactional
    public AdminVariantResponse update(Long id, AdminVariantRequest request) {
        if (request.initialStock() != null || request.alertThreshold() != null) {
            throw new BadRequestException("The stock and the alert threshold of an existing variant change through"
                    + " /variants/{id}/stock-adjustments and /variants/{id}/alert-threshold");
        }
        Variant variant = find(id);
        Product product = lockProduct(variant);
        apply(product, variant, request);
        return AdminVariantResponse.from(Persistence.orConflict(() -> variantRepository.saveAndFlush(variant),
                "A variant with SKU '" + variant.getSku() + "' already exists"));
    }

    /**
     * Only a variant of a draft: once a product has been on sale, its
     * variants may appear in orders. To stop selling one, set its stock to 0.
     */
    @Transactional
    public void delete(Long id) {
        Variant variant = find(id);
        Product product = lockProduct(variant);
        if (product.getStatus() != ProductStatus.BROUILLON) {
            throw new ConflictException("Only a variant of a draft product can be deleted: set the stock of '"
                    + variant.getSku() + "' to 0, or archive product '" + product.getSlug() + "'");
        }
        product.getVariants().remove(variant);
        productRepository.flush();
    }

    private void apply(Product product, Variant variant, AdminVariantRequest request) {
        String sku = request.sku().trim();
        variantRepository.findBySku(sku)
                .filter(other -> !Objects.equals(other.getId(), variant.getId()))
                .ifPresent(other -> {
                    throw new ConflictException("A variant with SKU '" + sku + "' already exists");
                });
        if (request.discountPriceExclTax() != null
                && request.discountPriceExclTax().compareTo(request.salePriceExclTax()) >= 0) {
            throw new BadRequestException("discountPriceExclTax must be below salePriceExclTax");
        }
        Set<AttributeValue> values = values(request.valueIds());
        rejectDuplicate(product, variant, values);

        variant.setSku(sku);
        variant.setPurchasePriceExclTax(money(request.purchasePriceExclTax()));
        variant.setSalePriceExclTax(money(request.salePriceExclTax()));
        variant.setDiscountPriceExclTax(money(request.discountPriceExclTax()));
        variant.setEcoTax(money(request.ecoTax()));
        // Autant de decimales qu'en base : la reponse est la meme avant et apres relecture.
        variant.setWeightKg(request.weightKg().setScale(3, RoundingMode.UNNECESSARY));
        variant.getValues().clear();
        variant.getValues().addAll(values);
    }

    /** The values of the variant: they must exist, and there is at most one per attribute. */
    private Set<AttributeValue> values(List<Long> ids) {
        Set<Long> wanted = new LinkedHashSet<>(ids);
        if (wanted.isEmpty()) {
            return new LinkedHashSet<>();
        }
        List<AttributeValue> found = attributeValueRepository.findWithAttributeByIdIn(wanted);
        if (found.size() != wanted.size()) {
            Set<Long> missing = new LinkedHashSet<>(wanted);
            found.forEach(value -> missing.remove(value.getId()));
            throw new BadRequestException("Unknown attribute values: " + missing);
        }
        Map<Long, AttributeValue> byAttribute = new HashMap<>();
        for (AttributeValue value : found) {
            AttributeValue other = byAttribute.putIfAbsent(value.getAttribute().getId(), value);
            if (other != null) {
                throw new BadRequestException("A variant has one value per attribute: '" + other.getValue()
                        + "' and '" + value.getValue() + "' are both " + value.getAttribute().getName());
            }
        }
        return new LinkedHashSet<>(found);
    }

    /**
     * Two variants of a product with the same values could not be told apart
     * on the product page: the customer picks values, not variants.
     */
    private static void rejectDuplicate(Product product, Variant variant, Set<AttributeValue> values) {
        Set<Long> ids = idsOf(values);
        product.getVariants().stream()
                .filter(other -> !Objects.equals(other.getId(), variant.getId()))
                .filter(other -> idsOf(other.getValues()).equals(ids))
                .findFirst()
                .ifPresent(other -> {
                    throw new ConflictException(ids.isEmpty()
                            ? "Product '" + product.getSlug() + "' already has a variant without attribute values ("
                                    + other.getSku() + "): give each variant its values"
                            : "Product '" + product.getSlug() + "' already has a variant with these values: "
                                    + other.getSku() + " (" + Designations.of(other) + ")");
                });
    }

    private static Set<Long> idsOf(Set<AttributeValue> values) {
        return values.stream().map(AttributeValue::getId).collect(Collectors.toSet());
    }

    /** Two decimals, as in the database; the request validation already refuses more. */
    private static BigDecimal money(BigDecimal amount) {
        return amount == null ? null : amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    private Product lockProduct(Variant variant) {
        Long productId = variant.getProduct().getId();
        return productRepository.findForUpdateById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product " + productId + " not found"));
    }

    private Variant find(Long id) {
        return variantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Variant " + id + " not found"));
    }
}
