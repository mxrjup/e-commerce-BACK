package com.ecommerce.catalog.service;

import com.ecommerce.catalog.dto.InternalVariantResponse;
import com.ecommerce.catalog.entity.Variant;
import com.ecommerce.catalog.repository.VariantRepository;
import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** What Cart, Order and the MCP read about variants (C10). */
@Service
@RequiredArgsConstructor
public class InternalCatalogService {

    /** Enough for any cart or order, small enough to keep one query cheap. */
    static final int MAX_IDS = 100;
    public static final int DEFAULT_ACCESSORY_LIMIT = 8;
    static final int MAX_ACCESSORY_LIMIT = 20;

    private final VariantRepository variantRepository;

    @Transactional(readOnly = true)
    public InternalVariantResponse variant(Long id) {
        return variantRepository.findWithDetailsByIdIn(List.of(id)).stream()
                .findFirst()
                .map(InternalVariantResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Variant " + id + " not found"));
    }

    /**
     * The variants in the order asked, each once. An unknown id is left out
     * rather than failing the whole call: a cart can hold a variant deleted
     * since, and must still display the rest.
     */
    @Transactional(readOnly = true)
    public List<InternalVariantResponse> variants(List<Long> ids) {
        Set<Long> wanted = ids == null ? Set.of()
                : ids.stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        if (wanted.isEmpty()) {
            throw new BadRequestException("ids must contain at least one variant id");
        }
        if (wanted.size() > MAX_IDS) {
            throw new BadRequestException("ids must contain at most " + MAX_IDS + " variant ids");
        }
        Map<Long, Variant> found = variantRepository.findWithDetailsByIdIn(wanted).stream()
                .collect(Collectors.toMap(Variant::getId, Function.identity()));
        return wanted.stream()
                .map(found::get)
                .filter(Objects::nonNull)
                .map(InternalVariantResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InternalVariantResponse> accessoryVariants(List<Long> ids, int limit) {
        Set<Long> wanted = ids == null ? Set.of()
                : ids.stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        if (wanted.isEmpty()) {
            throw new BadRequestException("ids must contain at least one variant id");
        }
        if (wanted.size() > MAX_IDS) {
            throw new BadRequestException("ids must contain at most " + MAX_IDS + " variant ids");
        }
        if (limit < 1 || limit > MAX_ACCESSORY_LIMIT) {
            throw new BadRequestException("limit must be between 1 and " + MAX_ACCESSORY_LIMIT);
        }

        Map<Long, Variant> cartVariants = variantRepository.findWithDetailsByIdIn(wanted).stream()
                .collect(Collectors.toMap(Variant::getId, Function.identity()));

        Set<Long> cartProductIds = wanted.stream()
                .map(cartVariants::get)
                .filter(Objects::nonNull)
                .map(variant -> variant.getProduct().getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (cartProductIds.isEmpty()) {
            return List.of();
        }

        List<InternalVariantResponse> candidates = variantRepository
                .findAccessoryVariantsBySourceProductIds(cartProductIds).stream()
                .map(InternalVariantResponse::from)
                .filter(InternalVariantResponse::available)
                .filter(variant -> !wanted.contains(variant.id()))
                .filter(variant -> !cartProductIds.contains(variant.productId()))
                .toList();

        Map<Long, InternalVariantResponse> byAccessoryProduct = new java.util.LinkedHashMap<>();
        for (InternalVariantResponse candidate : candidates) {
            byAccessoryProduct.putIfAbsent(candidate.productId(), candidate);
            if (byAccessoryProduct.size() >= limit) {
                break;
            }
        }
        return List.copyOf(byAccessoryProduct.values());
    }
}
