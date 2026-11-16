package com.ecommerce.catalog.service;

import com.ecommerce.catalog.dto.InventoryItemResponse;
import com.ecommerce.catalog.dto.PageResponse;
import com.ecommerce.catalog.repository.VariantRepository;
import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The stock of the variants (C07): the back-office inventory, manual
 * corrections, alert thresholds, and the decrement an order uses (C21).
 *
 * <p>Every change is a single conditional UPDATE: the stock never goes below
 * zero, even with concurrent requests, and the {@code ck_variante_stock}
 * constraint of {@code V2} backs this up in the database.
 */
@Service
@RequiredArgsConstructor
public class StockService {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 100;

    private final VariantRepository variantRepository;
    private final Clock clock = Clock.systemUTC();

    /**
     * @param search      part of a product title or a SKU, case-insensitive; null or blank for all
     * @param lowStockOnly only the variants at or below their alert threshold
     */
    @Transactional(readOnly = true)
    public PageResponse<InventoryItemResponse> inventory(String search, boolean lowStockOnly, int page, int size) {
        if (page < 0) {
            throw new BadRequestException("page must be positive or zero");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new BadRequestException("size must be between 1 and " + MAX_SIZE);
        }
        if ((long) page * size > Integer.MAX_VALUE) {
            throw new BadRequestException("page is too large");
        }
        Page<InventoryItemResponse> result = variantRepository
                .findInventory(pattern(search), lowStockOnly, PageRequest.of(page, size))
                .map(InventoryItemResponse::from);
        return PageResponse.of(result.getContent(), page, size, result.getTotalElements());
    }

    /** A manual correction: refused, and nothing changes, if the stock would go below zero. */
    @Transactional
    public InventoryItemResponse adjust(Long variantId, int delta) {
        requireExisting(variantId);
        if (variantRepository.addToStock(variantId, delta, now()) == 0) {
            throw new ConflictException("Variant " + variantId + " does not have enough stock to remove "
                    + Math.abs(delta) + " units");
        }
        return reload(variantId);
    }

    @Transactional
    public InventoryItemResponse setAlertThreshold(Long variantId, int threshold) {
        requireExisting(variantId);
        variantRepository.setAlertThreshold(variantId, threshold, now());
        return reload(variantId);
    }

    /**
     * Takes the quantities of an order out of the stock, all or nothing: if
     * one variant lacks stock, the transaction rolls back and no stock moves.
     * The variants are handled in id order, so two orders sharing variants
     * lock them in the same order and cannot deadlock.
     *
     * @param quantities variant id to quantity, each at least 1
     * @throws ConflictException when a variant is unknown or lacks stock
     */
    @Transactional
    public void decrement(Map<Long, Integer> quantities) {
        Instant now = now();
        for (Map.Entry<Long, Integer> line : new TreeMap<>(quantities).entrySet()) {
            if (line.getValue() == null || line.getValue() < 1) {
                throw new BadRequestException("quantity must be at least 1 for variant " + line.getKey());
            }
            if (variantRepository.addToStock(line.getKey(), -line.getValue(), now) == 0) {
                throw new ConflictException("Variant " + line.getKey() + " is unknown or does not have "
                        + line.getValue() + " units in stock");
            }
        }
    }

    private void requireExisting(Long variantId) {
        if (!variantRepository.existsById(variantId)) {
            throw new ResourceNotFoundException("Variant " + variantId + " not found");
        }
    }

    private InventoryItemResponse reload(Long variantId) {
        return variantRepository.findWithDetailsByIdIn(List.of(variantId)).stream()
                .findFirst()
                .map(InventoryItemResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Variant " + variantId + " not found"));
    }

    private Instant now() {
        return clock.instant();
    }

    /** A LIKE pattern with the user's text taken literally: % and _ match themselves. */
    private static String pattern(String search) {
        if (search == null || search.isBlank()) {
            return "%";
        }
        // "!" est le caractere d'echappement declare dans la requete (escape '!').
        String escaped = search.trim().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }
}
