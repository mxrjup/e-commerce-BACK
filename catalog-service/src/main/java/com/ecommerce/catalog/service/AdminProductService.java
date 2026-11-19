package com.ecommerce.catalog.service;

import com.ecommerce.catalog.dto.AdminProductRequest;
import com.ecommerce.catalog.dto.AdminProductResponse;
import com.ecommerce.catalog.dto.AdminProductSummary;
import com.ecommerce.catalog.dto.PageResponse;
import com.ecommerce.catalog.entity.Brand;
import com.ecommerce.catalog.entity.Category;
import com.ecommerce.catalog.entity.Product;
import com.ecommerce.catalog.entity.ProductSpecification;
import com.ecommerce.catalog.entity.ProductStatus;
import com.ecommerce.catalog.entity.Universe;
import com.ecommerce.catalog.entity.Variant;
import com.ecommerce.catalog.entity.VatRate;
import com.ecommerce.catalog.repository.BrandRepository;
import com.ecommerce.catalog.repository.CategoryRepository;
import com.ecommerce.catalog.repository.ProductRepository;
import com.ecommerce.catalog.repository.UniverseRepository;
import com.ecommerce.catalog.repository.VatRateRepository;
import com.ecommerce.catalog.support.CategoryTree;
import com.ecommerce.catalog.support.LikePatterns;
import com.ecommerce.catalog.support.Persistence;
import com.ecommerce.catalog.support.Slugs;
import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The back-office side of the products (C04): every status, the sheet and
 * its specifications, and the publication workflow.
 *
 * <p>A product starts as a draft. It is published once it has a variant,
 * then goes back and forth between published and archived, but never back
 * to draft: a draft is a product no customer has ever seen, which is what
 * makes deleting it harmless. A product that has been on sale may appear in
 * orders and support tickets, so it is archived instead.
 */
@Service
@RequiredArgsConstructor
public class AdminProductService {

    static final int MAX_SIZE = 100;
    static final int MAX_SPECIFICATION_KEYS = 50;

    private static final Map<String, Sort> SORTS = Map.of(
            "updated", Sort.by(Sort.Direction.DESC, "updatedAt", "id"),
            "newest", Sort.by(Sort.Direction.DESC, "createdAt", "id"),
            // Sans ignoreCase, H2 et PostgreSQL ne trient pas « ASUS » et « Apple » dans le meme ordre.
            "title", Sort.by(Sort.Order.asc("title").ignoreCase(), Sort.Order.asc("id")));

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;
    private final VatRateRepository vatRateRepository;
    private final UniverseRepository universeRepository;

    /**
     * @param search     part of the title, the slug or a SKU, case-insensitive; null or blank for all
     * @param categoryId the products of this category and of its sub-categories
     * @param sort       updated (default, last modified first), newest or title
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminProductSummary> list(String search, ProductStatus status, Long brandId,
                                                  Long categoryId, String sort, int page, int size) {
        if (page < 0) {
            throw new BadRequestException("page must be positive or zero");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new BadRequestException("size must be between 1 and " + MAX_SIZE);
        }
        if ((long) page * size > Integer.MAX_VALUE) {
            throw new BadRequestException("page is too large");
        }
        Sort order = sort == null || sort.isBlank() ? SORTS.get("updated") : SORTS.get(sort);
        if (order == null) {
            throw new BadRequestException("sort must be one of updated, newest, title");
        }
        Set<Long> categoryIds = categoryId == null ? null
                : CategoryTree.withDescendants(categoryRepository.findAll(), categoryId);

        Page<AdminProductSummary> result = productRepository
                .findAll(matching(search, status, brandId, categoryIds), PageRequest.of(page, size, order))
                .map(AdminProductSummary::from);
        return PageResponse.of(result.getContent(), page, size, result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public AdminProductResponse get(Long id) {
        return AdminProductResponse.from(find(id));
    }

    /** A new product is always a draft. */
    @Transactional
    public AdminProductResponse create(AdminProductRequest request) {
        return AdminProductResponse.from(save(new Product(), request));
    }

    @Transactional
    public AdminProductResponse update(Long id, AdminProductRequest request) {
        return AdminProductResponse.from(save(find(id), request));
    }

    @Transactional
    public AdminProductResponse changeStatus(Long id, ProductStatus status) {
        Product product = findForUpdate(id);
        if (status == product.getStatus()) {
            return AdminProductResponse.from(product);
        }
        if (status == ProductStatus.BROUILLON) {
            throw new ConflictException("Product '" + product.getSlug() + "' has left the draft status"
                    + " and cannot go back to it: archive it to take it off the storefront");
        }
        if (status == ProductStatus.PUBLIE && product.getVariants().isEmpty()) {
            throw new ConflictException("Product '" + product.getSlug()
                    + "' has no variant: add one before publishing it");
        }
        product.setStatus(status);
        return AdminProductResponse.from(productRepository.saveAndFlush(product));
    }

    /** Only a draft: it was never on sale, so no order nor ticket points to it. */
    @Transactional
    public void delete(Long id) {
        Product product = findForUpdate(id);
        if (product.getStatus() != ProductStatus.BROUILLON) {
            throw new ConflictException("Only a draft can be deleted: product '" + product.getSlug()
                    + "' is " + product.getStatus() + ", archive it instead");
        }
        productRepository.delete(product);
        productRepository.flush();
    }

    /** The specification keys already used in a category, most used first: suggestions for the form (C16). */
    @Transactional(readOnly = true)
    public List<String> specificationKeys(Long categoryId) {
        return productRepository.findSpecificationKeys(categoryId, PageRequest.of(0, MAX_SPECIFICATION_KEYS));
    }

    private Product save(Product product, AdminProductRequest request) {
        String title = request.title().trim();
        String slug = product.getId() == null
                ? Slugs.forCreation(request.slug(), title)
                : Slugs.forUpdate(request.slug(), product.getSlug());
        productRepository.findBySlug(slug)
                .filter(other -> !Objects.equals(other.getId(), product.getId()))
                .ifPresent(other -> {
                    throw new ConflictException("A product with slug '" + slug + "' already exists");
                });

        product.setTitle(title);
        product.setSlug(slug);
        product.setDescription(request.description() == null || request.description().isBlank()
                ? null : request.description());
        product.setBrand(brand(request.brandId()));
        product.setCategory(category(request.categoryId()));
        product.setVatRate(vatRate(product, request.vatRateId()));
        product.getUniverses().clear();
        product.getUniverses().addAll(universes(request.universeIds()));
        replaceSpecifications(product, request.specifications() == null ? List.of() : request.specifications());
        return Persistence.orConflict(() -> productRepository.saveAndFlush(product),
                "A product with this slug already exists");
    }

    /**
     * Replaces the whole list. A key kept from one version to the next keeps
     * its row and only changes value and position: deleting it and inserting
     * it again would hit {@code uk_produit_specification}, because Hibernate
     * runs the inserts before the deletes.
     */
    private static void replaceSpecifications(Product product, List<AdminProductRequest.Specification> requested) {
        Set<String> seen = new HashSet<>();
        for (AdminProductRequest.Specification specification : requested) {
            if (!seen.add(specification.key().trim().toLowerCase(Locale.ROOT))) {
                throw new BadRequestException("Specification '" + specification.key().trim() + "' appears twice");
            }
        }
        Map<String, ProductSpecification> current = new LinkedHashMap<>();
        product.getSpecifications().forEach(specification -> current.put(specification.getKey(), specification));

        List<ProductSpecification> next = new ArrayList<>();
        for (AdminProductRequest.Specification requestedSpecification : requested) {
            String key = requestedSpecification.key().trim();
            ProductSpecification specification = current.remove(key);
            if (specification == null) {
                specification = new ProductSpecification();
                specification.setProduct(product);
                specification.setKey(key);
            }
            specification.setValue(requestedSpecification.value().trim());
            specification.setPosition(next.size());
            next.add(specification);
        }
        product.getSpecifications().clear();
        product.getSpecifications().addAll(next);
    }

    private Brand brand(Long id) {
        return brandRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Brand " + id + " does not exist"));
    }

    private Category category(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Category " + id + " does not exist"));
    }

    /**
     * Without an id: the default rate for a new product, the current one
     * otherwise. An inactive rate is refused, unless the product already has it.
     */
    private VatRate vatRate(Product product, Long id) {
        if (id == null && product.getVatRate() != null) {
            return product.getVatRate();
        }
        VatRate vatRate = id == null
                ? vatRateRepository.findByDefaultRateTrue()
                        .orElseThrow(() -> new BadRequestException("There is no default VAT rate: send vatRateId"))
                : vatRateRepository.findById(id)
                        .orElseThrow(() -> new BadRequestException("VAT rate " + id + " does not exist"));
        boolean alreadyUsed = product.getVatRate() != null && product.getVatRate().getId().equals(vatRate.getId());
        if (!vatRate.isActive() && !alreadyUsed) {
            throw new BadRequestException("VAT rate '" + vatRate.getLabel() + "' is inactive: choose an active rate");
        }
        return vatRate;
    }

    private List<Universe> universes(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Set<Long> wanted = new LinkedHashSet<>(ids);
        List<Universe> found = universeRepository.findAllById(wanted);
        if (found.size() != wanted.size()) {
            Set<Long> missing = new LinkedHashSet<>(wanted);
            found.forEach(universe -> missing.remove(universe.getId()));
            throw new BadRequestException("Unknown universes: " + missing);
        }
        return found;
    }

    private static Specification<Product> matching(String search, ProductStatus status, Long brandId,
                                                   Set<Long> categoryIds) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (brandId != null) {
                predicates.add(cb.equal(root.get("brand").get("id"), brandId));
            }
            if (categoryIds != null) {
                predicates.add(root.get("category").get("id").in(categoryIds));
            }
            if (search != null && !search.isBlank()) {
                // "!" est le caractere d'echappement des motifs de LikePatterns.
                String pattern = LikePatterns.contains(search);
                Subquery<Long> sku = query.subquery(Long.class);
                Root<Variant> variant = sku.from(Variant.class);
                sku.select(variant.get("id")).where(
                        cb.equal(variant.get("product"), root),
                        cb.like(cb.lower(variant.get("sku")), pattern, '!'));
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern, '!'),
                        cb.like(cb.lower(root.get("slug")), pattern, '!'),
                        cb.exists(sku)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Product find(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product " + id + " not found"));
    }

    private Product findForUpdate(Long id) {
        return productRepository.findForUpdateById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product " + id + " not found"));
    }
}
