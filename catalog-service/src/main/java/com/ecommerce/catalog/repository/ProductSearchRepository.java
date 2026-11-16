package com.ecommerce.catalog.repository;

import com.ecommerce.catalog.dto.CatalogFacetsResponse;
import com.ecommerce.catalog.dto.CatalogFacetsResponse.*;
import com.ecommerce.catalog.entity.Product;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    private enum Facet { NONE, CATEGORY, BRAND, UNIVERSE, PRICE, ATTRIBUTE, SPECIFICATION }

    public CatalogFacetsResponse facets(Criteria criteria) {
        List<ReferenceFacet> categories = references(criteria, Facet.CATEGORY, "c");
        List<ReferenceFacet> brands = references(criteria, Facet.BRAND, "b");
        Filter universesFilter = filter(criteria, Facet.UNIVERSE);
        List<ReferenceFacet> universes = jdbc.query(PRICED +
                "select u.slug, u.nom, count(distinct p.id)" + FROM + """
                 join produit_univers pu on pu.produit_id = p.id join univers u on u.id = pu.univers_id
                """ + universesFilter.sql() + " group by u.slug, u.nom order by u.nom, u.slug",
                universesFilter.params(), (rs, n) -> new ReferenceFacet(rs.getString(1), rs.getString(2), rs.getLong(3)));
        Filter priceFilter = filter(criteria, Facet.PRICE);
        PriceRange price = jdbc.queryForObject(PRICED + "select min(pr.price), max(pr.price)" + FROM
                + priceFilter.sql(), priceFilter.params(),
                (rs, n) -> new PriceRange(rs.getBigDecimal(1), rs.getBigDecimal(2)));
        Filter attributesFilter = filter(criteria, Facet.ATTRIBUTE);
        List<ValueFacet> attributes = jdbc.query(PRICED +
                "select fa.nom, av.valeur, count(distinct p.id)" + FROM + """
                 join variante fv on fv.produit_id = p.id join variante_valeur vv on vv.variante_id = fv.id
                join valeur_attribut av on av.id = vv.valeur_id join attribut fa on fa.id = av.attribut_id
                """ + attributesFilter.sql() + " group by fa.nom, av.valeur order by fa.nom, av.valeur",
                attributesFilter.params(), (rs, n) -> new ValueFacet(rs.getString(1), rs.getString(2), rs.getLong(3)));
        Filter specificationsFilter = filter(criteria, Facet.SPECIFICATION);
        List<ValueFacet> specifications = jdbc.query(PRICED +
                "select fs.cle, fs.valeur, count(distinct p.id)" + FROM
                + " join produit_specification fs on fs.produit_id = p.id"
                + specificationsFilter.sql() + " group by fs.cle, fs.valeur order by fs.cle, fs.valeur",
                specificationsFilter.params(), (rs, n) -> new ValueFacet(rs.getString(1), rs.getString(2), rs.getLong(3)));
        return new CatalogFacetsResponse(categories, brands, universes, price, attributes, specifications);
    }

    private List<ReferenceFacet> references(Criteria criteria, Facet facet, String alias) {
        Filter filter = filter(criteria, facet);
        // Aliases are internal constants, never request parameters.
        return jdbc.query(PRICED + "select " + alias + ".slug, " + alias + ".nom, count(distinct p.id)"
                + FROM + filter.sql() + " group by " + alias + ".slug, " + alias + ".nom order by "
                + alias + ".nom, " + alias + ".slug", filter.params(),
                (rs, n) -> new ReferenceFacet(rs.getString(1), rs.getString(2), rs.getLong(3)));
    }

    /** PostgreSQL uses indexed trigram similarity; H2 falls back to literal substring matching. */
    public List<Product> autocomplete(String query, int limit) {
        boolean postgres = Boolean.TRUE.equals(jdbc.getJdbcTemplate().execute((ConnectionCallback<Boolean>)
                connection -> "PostgreSQL".equals(connection.getMetaData().getDatabaseProductName())));
        String matching = postgres
                ? "(lower(p.titre) like lower(:pattern) escape '!' or lower(p.titre) %> lower(:query))"
                : folded("p.titre") + " like " + folded(":pattern") + " escape '!'";
        String order = postgres
                ? "word_similarity(lower(:query), lower(p.titre)) desc,"
                    + " similarity(lower(:query), lower(p.titre)) desc, lower(p.titre), p.id"
                : "case when lower(p.titre) = lower(:query) then 0 else 1 end, lower(p.titre), p.id";
        List<Long> ids = jdbc.queryForList("select p.id from produit p where p.statut = 'PUBLIE'"
                + " and exists (select 1 from variante v where v.produit_id = p.id) and " + matching
                + " order by " + order + " limit :limit", new MapSqlParameterSource("query", query)
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
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    private record Filter(String sql, MapSqlParameterSource params) { }

    private static Filter filter(Criteria criteria) {
        return filter(criteria, Facet.NONE);
    }

    /** Each facet removes only its own selection, retaining q and all other criteria. */
    private static Filter filter(Criteria criteria, Facet facet) {
        StringBuilder sql = new StringBuilder(" where p.statut = 'PUBLIE'");
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (criteria.categoryIds() != null && facet != Facet.CATEGORY) {
            if (criteria.categoryIds().isEmpty()) sql.append(" and 1 = 0");
            else {
                sql.append(" and p.categorie_id in (:categories)");
                params.addValue("categories", criteria.categoryIds());
            }
        }
        if (criteria.brandSlug() != null && facet != Facet.BRAND) {
            sql.append(" and b.slug = :brand");
            params.addValue("brand", criteria.brandSlug());
        }
        if (criteria.universeSlug() != null && facet != Facet.UNIVERSE) {
            sql.append(" and exists (select 1 from produit_univers pu join univers u on u.id = pu.univers_id"
                    + " where pu.produit_id = p.id and u.slug = :universe)");
            params.addValue("universe", criteria.universeSlug());
        }
        if (criteria.query() != null) {
            sql.append(" and (").append(folded("p.titre")).append(" like ").append(folded(":query"))
                    .append(" escape '!' or ").append(folded("c.nom")).append(" like ").append(folded(":query"))
                    .append(" escape '!' or ").append(folded("b.nom")).append(" like ").append(folded(":query"))
                    .append(" escape '!')");
            params.addValue("query", "%" + escapeLike(criteria.query()) + "%");
        }
        if (criteria.minPrice() != null && facet != Facet.PRICE) {
            sql.append(" and pr.price >= :minPrice");
            params.addValue("minPrice", criteria.minPrice());
        }
        if (criteria.maxPrice() != null && facet != Facet.PRICE) {
            sql.append(" and pr.price <= :maxPrice");
            params.addValue("maxPrice", criteria.maxPrice());
        }
        // All selected attributes must coexist on one variant (OR within one attribute).
        if (!criteria.attributes().isEmpty()) {
            sql.append(" and exists (select 1 from variante selected where selected.produit_id = p.id");
            // A displayed value must coexist with the other selected attributes on this variant.
            if (facet == Facet.ATTRIBUTE) sql.append(" and selected.id = fv.id");
            int i = 0;
            for (var entry : criteria.attributes().entrySet()) {
                sql.append(" and (");
                if (facet == Facet.ATTRIBUTE) sql.append("fa.nom = :ak").append(i).append(" or ");
                sql.append("exists (select 1 from variante_valeur vv join valeur_attribut av on av.id = vv.valeur_id"
                        + " join attribut a on a.id = av.attribut_id where vv.variante_id = selected.id"
                        + " and a.nom = :ak" + i + " and av.valeur in (:av" + i + ")))");
                params.addValue("ak" + i, entry.getKey()).addValue("av" + i, entry.getValue());
                i++;
            }
            sql.append(")");
        }
        int i = 0;
        for (var entry : criteria.specifications().entrySet()) {
            sql.append(" and (");
            if (facet == Facet.SPECIFICATION) sql.append("fs.cle = :sk").append(i).append(" or ");
            sql.append("exists (select 1 from produit_specification s where s.produit_id = p.id"
                    + " and s.cle = :sk" + i + " and s.valeur in (:sv" + i + ")))");
            params.addValue("sk" + i, entry.getKey()).addValue("sv" + i, entry.getValue());
            i++;
        }
        return new Filter(sql.toString(), params);
    }

    /** French/Latin accents, using the same SQL on H2 and PostgreSQL without an extra extension. */
    private static String folded(String expression) {
        return "replace(replace(translate(lower(" + expression
                + "), 'àâäáãåçèéêëìíîïñòóôöõùúûüýÿ', 'aaaaaaceeeeiiiinooooouuuuyy'), 'œ', 'oe'), 'æ', 'ae')";
    }

    private static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
