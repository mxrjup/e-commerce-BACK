package com.ecommerce.catalog.repository;

import com.ecommerce.catalog.dto.CatalogFacetsResponse;
import com.ecommerce.catalog.dto.CatalogFacetsResponse.*;
import com.ecommerce.catalog.entity.Product;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** SQL selects and paginates IDs before loading entities: collection fetches never paginate in memory. */
@Repository
@RequiredArgsConstructor
public class ProductSearchRepository {
    private final EntityManager entityManager;
    private final NamedParameterJdbcTemplate jdbc;

    // Round VAT separately, exactly as Prices.inclTax does. Keep C02's unrounded price sort.
    private static final String PRICED = """
            with prices as (
              select v.produit_id,
                min((coalesce(v.prix_remise_ht, v.prix_vente_ht) + coalesce(v.ecotaxe, 0))
                    * (100 + t.taux)) as sort_price,
                min(coalesce(v.prix_remise_ht, v.prix_vente_ht) + coalesce(v.ecotaxe, 0)
                    + round((coalesce(v.prix_remise_ht, v.prix_vente_ht) + coalesce(v.ecotaxe, 0))
                            * t.taux / 100, 2)) as price
              from variante v join produit p on p.id = v.produit_id join tva t on t.id = p.tva_id
              group by v.produit_id
            )
            """;
    private static final String FROM = " from produit p join prices pr on pr.produit_id = p.id"
            + " join marque b on b.id = p.marque_id join categorie c on c.id = p.categorie_id";

    public enum Sort {
        NEWEST("p.date_creation desc, p.id desc"),
        PRICE_ASC("pr.sort_price asc, p.id asc"),
        PRICE_DESC("pr.sort_price desc, p.id asc"),
        NAME("lower(p.titre) asc, p.id asc");
        private final String orderBy;
        Sort(String orderBy) { this.orderBy = orderBy; }
    }

    public record Criteria(Collection<Long> categoryIds, String brandSlug, String universeSlug,
                           String query, BigDecimal minPrice, BigDecimal maxPrice,
                           Map<String, List<String>> attributes, Map<String, List<String>> specifications) {
        public Criteria(Collection<Long> categoryIds, String brandSlug, String universeSlug) {
            this(categoryIds, brandSlug, universeSlug, null, null, null, Map.of(), Map.of());
        }
    }

    public long count(Criteria criteria) {
        Filter filter = filter(criteria);
        return jdbc.queryForObject(PRICED + "select count(*)" + FROM + filter.sql(), filter.params(), Long.class);
    }

    public List<Product> page(Criteria criteria, Sort sort, int page, int size) {
        Filter filter = filter(criteria);
        filter.params().addValue("limit", size).addValue("offset", (long) page * size);
        List<Long> ids = jdbc.queryForList(PRICED + "select p.id" + FROM + filter.sql()
                + " order by " + sort.orderBy + " limit :limit offset :offset", filter.params(), Long.class);
        return products(ids);
    }

    public CatalogFacetsResponse facets(Criteria criteria) {
        Filter filter = filter(criteria);
        String matched = PRICED + ", matched as (select p.id, p.marque_id, p.categorie_id, pr.price"
                + FROM + filter.sql() + ") ";
        List<ReferenceFacet> categories = references(matched, "categorie", "categorie_id", filter.params());
        List<ReferenceFacet> brands = references(matched, "marque", "marque_id", filter.params());
        List<ReferenceFacet> universes = jdbc.query(matched + """
                select u.slug, u.nom, count(distinct m.id) as total from matched m
                join produit_univers pu on pu.produit_id = m.id join univers u on u.id = pu.univers_id
                group by u.slug, u.nom order by u.nom, u.slug
                """, filter.params(), (rs, n) -> new ReferenceFacet(rs.getString(1), rs.getString(2), rs.getLong(3)));
        PriceRange price = jdbc.queryForObject(matched + "select min(price), max(price) from matched",
                filter.params(), (rs, n) -> new PriceRange(rs.getBigDecimal(1), rs.getBigDecimal(2)));
        List<ValueFacet> attributes = jdbc.query(matched + """
                select a.nom, av.valeur, count(distinct m.id) from matched m
                join variante v on v.produit_id = m.id join variante_valeur vv on vv.variante_id = v.id
                join valeur_attribut av on av.id = vv.valeur_id join attribut a on a.id = av.attribut_id
                group by a.nom, av.valeur order by a.nom, av.valeur
                """, filter.params(), (rs, n) -> new ValueFacet(rs.getString(1), rs.getString(2), rs.getLong(3)));
        List<ValueFacet> specifications = jdbc.query(matched + """
                select s.cle, s.valeur, count(distinct m.id) from matched m
                join produit_specification s on s.produit_id = m.id
                group by s.cle, s.valeur order by s.cle, s.valeur
                """, filter.params(), (rs, n) -> new ValueFacet(rs.getString(1), rs.getString(2), rs.getLong(3)));
        return new CatalogFacetsResponse(categories, brands, universes, price, attributes, specifications);
    }

    private List<ReferenceFacet> references(String matched, String table, String foreignKey,
                                             MapSqlParameterSource params) {
        // Identifiers are internal constants, never request parameters.
        return jdbc.query(matched + "select r.slug, r.nom, count(*) from matched m join " + table
                + " r on r.id = m." + foreignKey + " group by r.slug, r.nom order by r.nom, r.slug",
                params, (rs, n) -> new ReferenceFacet(rs.getString(1), rs.getString(2), rs.getLong(3)));
    }

    /** PostgreSQL-only: indexed word similarity also matches a typo inside a long product title. */
    public List<Product> autocomplete(String query, int limit) {
        List<Long> ids = jdbc.queryForList("""
                select p.id from produit p where p.statut = 'PUBLIE'
                  and exists (select 1 from variante v where v.produit_id = p.id)
                  and (lower(p.titre) like lower(:pattern) escape '!'
                       or lower(p.titre) %> lower(:query))
                order by word_similarity(lower(:query), lower(p.titre)) desc,
                         similarity(lower(:query), lower(p.titre)) desc, lower(p.titre), p.id
                limit :limit
                """, new MapSqlParameterSource("query", query)
                .addValue("pattern", "%" + escapeLike(query) + "%").addValue("limit", limit), Long.class);
        return products(ids);
    }

    private List<Product> products(List<Long> ids) {
        if (ids.isEmpty()) return List.of();
        Map<Long, Product> byId = entityManager.createQuery(
                        "select p from Product p join fetch p.brand join fetch p.category join fetch p.vatRate"
                                + " where p.id in :ids", Product.class)
                .setParameter("ids", ids).getResultList().stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        return ids.stream().map(byId::get).toList();
    }

    private record Filter(String sql, MapSqlParameterSource params) { }

    private static Filter filter(Criteria criteria) {
        StringBuilder sql = new StringBuilder(" where p.statut = 'PUBLIE'");
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (criteria.categoryIds() != null) {
            if (criteria.categoryIds().isEmpty()) sql.append(" and 1 = 0");
            else {
                sql.append(" and p.categorie_id in (:categories)");
                params.addValue("categories", criteria.categoryIds());
            }
        }
        if (criteria.brandSlug() != null) {
            sql.append(" and b.slug = :brand");
            params.addValue("brand", criteria.brandSlug());
        }
        if (criteria.universeSlug() != null) {
            sql.append(" and exists (select 1 from produit_univers pu join univers u on u.id = pu.univers_id"
                    + " where pu.produit_id = p.id and u.slug = :universe)");
            params.addValue("universe", criteria.universeSlug());
        }
        if (criteria.query() != null) {
            sql.append(" and lower(p.titre) like lower(:query) escape '!'");
            params.addValue("query", "%" + escapeLike(criteria.query()) + "%");
        }
        if (criteria.minPrice() != null) {
            sql.append(" and pr.price >= :minPrice");
            params.addValue("minPrice", criteria.minPrice());
        }
        if (criteria.maxPrice() != null) {
            sql.append(" and pr.price <= :maxPrice");
            params.addValue("maxPrice", criteria.maxPrice());
        }
        // All selected attributes must coexist on one variant (OR within one attribute).
        if (!criteria.attributes().isEmpty()) {
            sql.append(" and exists (select 1 from variante selected where selected.produit_id = p.id");
            int i = 0;
            for (var entry : criteria.attributes().entrySet()) {
                sql.append(" and exists (select 1 from variante_valeur vv join valeur_attribut av on av.id = vv.valeur_id"
                        + " join attribut a on a.id = av.attribut_id where vv.variante_id = selected.id"
                        + " and a.nom = :ak" + i + " and av.valeur in (:av" + i + "))");
                params.addValue("ak" + i, entry.getKey()).addValue("av" + i, entry.getValue());
                i++;
            }
            sql.append(")");
        }
        int i = 0;
        for (var entry : criteria.specifications().entrySet()) {
            sql.append(" and exists (select 1 from produit_specification s where s.produit_id = p.id"
                    + " and s.cle = :sk" + i + " and s.valeur in (:sv" + i + "))");
            params.addValue("sk" + i, entry.getKey()).addValue("sv" + i, entry.getValue());
            i++;
        }
        return new Filter(sql.toString(), params);
    }

    private static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
