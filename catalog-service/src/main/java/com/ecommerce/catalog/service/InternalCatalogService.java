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
}
