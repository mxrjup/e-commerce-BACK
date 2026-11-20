package com.ecommerce.catalog.service;

import com.ecommerce.catalog.dto.CatalogFacetsResponse;
import com.ecommerce.catalog.dto.PageResponse;
import com.ecommerce.catalog.dto.ProductCardResponse;
import com.ecommerce.catalog.dto.ProductDetailResponse;
import com.ecommerce.catalog.entity.Category;
import com.ecommerce.catalog.entity.ProductStatus;
import com.ecommerce.catalog.repository.CategoryRepository;
import com.ecommerce.catalog.repository.ProductRepository;
import com.ecommerce.catalog.repository.ProductSearchRepository;
import com.ecommerce.catalog.support.CategoryTree;
import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.common.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
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
        return list(category, brand, universe, sort, page, size, null, null, null, List.of(), List.of());
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductCardResponse> list(String category, String brand, String universe,
            String sort, int page, int size, String q, BigDecimal minPrice, BigDecimal maxPrice,
            List<String> attributes, List<String> specifications) {
        ProductSearchRepository.Criteria criteria = criteria(category, brand, universe, q,
                minPrice, maxPrice, attributes, specifications);
        if (page < 0) {
            throw new BadRequestException("page must be positive or zero");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new BadRequestException("size must be between 1 and " + MAX_SIZE);
        }
        // Preserve C02's maximum offset, even though SQL now receives a long.
        if ((long) page * size > Integer.MAX_VALUE) {
            throw new BadRequestException("page is too large");
        }
        ProductSearchRepository.Sort order = sort == null || sort.isBlank()
                ? ProductSearchRepository.Sort.NEWEST : SORTS.get(sort);
        if (order == null) {
            throw new BadRequestException("sort must be one of newest, price-asc, price-desc, name");
        }

        long total = searchRepository.count(criteria);
        List<ProductCardResponse> content = total == 0 ? List.of()
                : searchRepository.page(criteria, order, page, size).stream().map(ProductCardResponse::from).toList();
        return PageResponse.of(content, page, size, total);
    }

    @Transactional(readOnly = true)
    public CatalogFacetsResponse facets(String category, String brand, String universe, String q,
            BigDecimal minPrice, BigDecimal maxPrice, List<String> attributes, List<String> specifications) {
        return searchRepository.facets(criteria(category, brand, universe, q, minPrice, maxPrice,
                attributes, specifications));
    }

    @Transactional(readOnly = true)
    public List<ProductCardResponse> autocomplete(String q, int limit) {
        if (limit < 1 || limit > 20) throw new BadRequestException("limit must be between 1 and 20");
        String query = normalizeQuery(q);
        if (query == null) return List.of();
        return searchRepository.autocomplete(query, limit).stream().map(ProductCardResponse::from).toList();
    }

    private ProductSearchRepository.Criteria criteria(String category, String brand, String universe,
            String q, BigDecimal minPrice, BigDecimal maxPrice, List<String> attributes,
            List<String> specifications) {
        if ((minPrice != null && minPrice.signum() < 0) || (maxPrice != null && maxPrice.signum() < 0)) {
            throw new BadRequestException("prices must be positive or zero");
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BadRequestException("minPrice must not exceed maxPrice");
        }
        String query = normalizeQuery(q);
        Map<String, List<String>> attributeFilters = parseValues(attributes, 50, 50);
        Map<String, List<String>> specificationFilters = parseValues(specifications, 100, 500);
        Collection<Long> categoryIds = blankToNull(category) == null ? null : categoryAndDescendants(category);
        return new ProductSearchRepository.Criteria(categoryIds, blankToNull(brand), blankToNull(universe),
                query, minPrice, maxPrice, attributeFilters, specificationFilters);
    }

    private static String normalizeQuery(String q) {
        if (q != null && q.length() > 150) throw new BadRequestException("q must not exceed 150 characters");
        return q == null || q.isBlank() ? null : q.strip();
    }

    /** Repeated name:value parameters: AND between names, OR between values of one name. */
    private static Map<String, List<String>> parseValues(List<String> filters, int keyLength, int valueLength) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (filters == null) return result;
        if (filters.size() > 20) throw new BadRequestException("at most 20 attribute or specification filters are allowed");
        for (String filter : filters) {
            int separator = filter == null ? -1 : filter.indexOf(':');
            if (separator <= 0 || separator == filter.length() - 1) {
                throw new BadRequestException("filters must use name:value");
            }
            String key = filter.substring(0, separator).strip();
            String value = filter.substring(separator + 1).strip();
            if (key.isEmpty() || value.isEmpty() || key.length() > keyLength || value.length() > valueLength) {
                throw new BadRequestException("invalid attribute or specification name/value");
            }
            result.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
        }
        return result;
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
        return all.stream()
                .filter(category -> category.getSlug().equals(slug))
                .findFirst()
                .map(root -> CategoryTree.withDescendants(all, root.getId()))
                .orElseGet(Set::of);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
