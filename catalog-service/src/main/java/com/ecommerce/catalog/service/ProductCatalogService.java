package com.ecommerce.catalog.service;

import com.ecommerce.catalog.dto.PageResponse;
import com.ecommerce.catalog.dto.ProductCardResponse;
import com.ecommerce.catalog.dto.ProductDetailResponse;
import com.ecommerce.catalog.entity.Category;
import com.ecommerce.catalog.entity.ProductStatus;
import com.ecommerce.catalog.repository.CategoryRepository;
import com.ecommerce.catalog.repository.ProductRepository;
import com.ecommerce.catalog.repository.ProductSearchRepository;
import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The storefront side of the products (C02): published products only. */
@Service
@RequiredArgsConstructor
public class ProductCatalogService {

    public static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 50;

    private static final Map<String, ProductSearchRepository.Sort> SORTS = Map.of(
            "newest", ProductSearchRepository.Sort.NEWEST,
            "price-asc", ProductSearchRepository.Sort.PRICE_ASC,
            "price-desc", ProductSearchRepository.Sort.PRICE_DESC,
            "name", ProductSearchRepository.Sort.NAME);

    private final ProductSearchRepository searchRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    /**
     * @param category a category slug; its sub-categories are included. An unknown slug gives an empty page.
     * @param sort     newest (default), price-asc, price-desc or name
     */
    @Transactional(readOnly = true)
    public PageResponse<ProductCardResponse> list(String category, String brand, String universe,
                                                  String sort, int page, int size) {
        if (page < 0) {
            throw new BadRequestException("page must be positive or zero");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new BadRequestException("size must be between 1 and " + MAX_SIZE);
        }
        // L'offset page * size est un int pour Hibernate : au-dela, il deborderait en negatif.
        if ((long) page * size > Integer.MAX_VALUE) {
            throw new BadRequestException("page is too large");
        }
        ProductSearchRepository.Sort order = sort == null || sort.isBlank()
                ? ProductSearchRepository.Sort.NEWEST : SORTS.get(sort);
        if (order == null) {
            throw new BadRequestException("sort must be one of newest, price-asc, price-desc, name");
        }

        Collection<Long> categoryIds = null;
        if (category != null && !category.isBlank()) {
            categoryIds = categoryAndDescendants(category);
            if (categoryIds.isEmpty()) {
                return PageResponse.of(List.of(), page, size, 0);
            }
        }
        ProductSearchRepository.Criteria criteria = new ProductSearchRepository.Criteria(
                categoryIds, blankToNull(brand), blankToNull(universe));

        long total = searchRepository.count(criteria);
        List<ProductCardResponse> content = total == 0 ? List.of()
                : searchRepository.page(criteria, order, page, size).stream().map(ProductCardResponse::from).toList();
        return PageResponse.of(content, page, size, total);
    }

    /** A published product; a draft or an archived one is not found, like an unknown slug. */
    @Transactional(readOnly = true)
    public ProductDetailResponse get(String slug) {
        return productRepository.findBySlug(slug)
                .filter(product -> product.getStatus() == ProductStatus.PUBLIE)
                .filter(product -> !product.getVariants().isEmpty())
                .map(ProductDetailResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Product '" + slug + "' not found"));
    }

    /** The ids of a category and of every category below it; empty when the slug is unknown. */
    private Set<Long> categoryAndDescendants(String slug) {
        List<Category> all = categoryRepository.findAll();
        Map<Long, List<Long>> children = new HashMap<>();
        Long root = null;
        for (Category category : all) {
            if (category.getParentId() != null) {
                children.computeIfAbsent(category.getParentId(), key -> new ArrayList<>()).add(category.getId());
            }
            if (category.getSlug().equals(slug)) {
                root = category.getId();
            }
        }
        Set<Long> ids = new LinkedHashSet<>();
        if (root == null) {
            return ids;
        }
        Deque<Long> toVisit = new ArrayDeque<>(List.of(root));
        while (!toVisit.isEmpty()) {
            Long id = toVisit.pop();
            if (ids.add(id)) {
                toVisit.addAll(children.getOrDefault(id, List.of()));
            }
        }
        return ids;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
