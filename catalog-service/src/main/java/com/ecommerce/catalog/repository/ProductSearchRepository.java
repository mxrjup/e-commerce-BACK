package com.ecommerce.catalog.repository;

import com.ecommerce.catalog.entity.Product;
import com.ecommerce.catalog.entity.ProductStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The storefront product list (C02): published products, filtered and
 * sorted in the database, one page at a time.
 *
 * <p>Two steps: first the ids of the page, sorted (by the cheapest variant
 * for a price sort), then the products of those ids. Paginating a query that
 * fetches collections would make Hibernate paginate in memory.
 */
@Repository
@RequiredArgsConstructor
public class ProductSearchRepository {

    /** The price including VAT of a variant, as an expression: what the price sorts compare. */
    private static final String PRICE_INCL_TAX = "min((coalesce(v.discountPriceExclTax, v.salePriceExclTax)"
            + " + coalesce(v.ecoTax, :zero)) * (100 + t.rate))";

    private final EntityManager entityManager;

    public enum Sort {
        NEWEST("p.createdAt desc, p.id desc"),
        PRICE_ASC("price asc, p.id asc"),
        PRICE_DESC("price desc, p.id asc"),
        /** Case-insensitive, so "Apple" comes before "ASUS" on H2 as on PostgreSQL. */
        NAME("lower(p.title) asc, p.id asc");

        private final String orderBy;

        Sort(String orderBy) {
            this.orderBy = orderBy;
        }
    }

    /**
     * @param categoryIds the category and its sub-categories, or null for any
     * @param brandSlug   or null for any
     * @param universeSlug or null for any
     */
    public record Criteria(Collection<Long> categoryIds, String brandSlug, String universeSlug) {
    }

    public long count(Criteria criteria) {
        TypedQuery<Long> query = entityManager.createQuery(
                "select count(distinct p.id) " + from(criteria) + where(criteria), Long.class);
        bind(query, criteria, false);
        return query.getSingleResult();
    }

    /** The products of one page, in the order asked, with their brand, category and VAT rate loaded. */
    public List<Product> page(Criteria criteria, Sort sort, int page, int size) {
        TypedQuery<Object[]> idsQuery = entityManager.createQuery(
                "select p.id, " + PRICE_INCL_TAX + " as price " + from(criteria) + where(criteria)
                        + " group by p.id, p.createdAt, p.title order by " + sort.orderBy, Object[].class);
        bind(idsQuery, criteria, true);
        List<Long> ids = idsQuery.setFirstResult(page * size).setMaxResults(size).getResultList().stream()
                .map(row -> (Long) row[0])
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Product> byId = entityManager.createQuery(
                        "select p from Product p join fetch p.brand join fetch p.category join fetch p.vatRate"
                                + " where p.id in :ids", Product.class)
                .setParameter("ids", ids)
                .getResultList().stream()
                .collect(Collectors.toMap(Product::getId, Function.identity(), (a, b) -> a, HashMap::new));
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    /** Products without a variant cannot be sold, so the join on the variants leaves them out. */
    private static String from(Criteria criteria) {
        return "from Product p join p.variants v join p.vatRate t"
                + (criteria.universeSlug() != null ? " join p.universes u" : "");
    }

    private static String where(Criteria criteria) {
        StringBuilder where = new StringBuilder(" where p.status = :status");
        if (criteria.categoryIds() != null) {
            where.append(" and p.category.id in :categoryIds");
        }
        if (criteria.brandSlug() != null) {
            where.append(" and p.brand.slug = :brand");
        }
        if (criteria.universeSlug() != null) {
            where.append(" and u.slug = :universe");
        }
        return where.toString();
    }

    private static void bind(TypedQuery<?> query, Criteria criteria, boolean withPrice) {
        query.setParameter("status", ProductStatus.PUBLIE);
        if (withPrice) {
            query.setParameter("zero", BigDecimal.ZERO);
        }
        if (criteria.categoryIds() != null) {
            query.setParameter("categoryIds", criteria.categoryIds());
        }
        if (criteria.brandSlug() != null) {
            query.setParameter("brand", criteria.brandSlug());
        }
        if (criteria.universeSlug() != null) {
            query.setParameter("universe", criteria.universeSlug());
        }
    }
}
