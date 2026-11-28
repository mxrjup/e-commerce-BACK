package com.ecommerce.catalog.support;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Empties the catalogue tables, products first: they reference brands,
 * categories, universes and VAT rates, which other tests delete freely.
 * The four VAT rates of {@code V1} are kept.
 */
public final class CatalogTestData {

    private CatalogTestData() {
    }

    public static void clear(JdbcTemplate jdbc) {
        // Les variantes, specifications, images et liens univers suivent par ON DELETE CASCADE.
        jdbc.update("DELETE FROM produit");
        jdbc.update("DELETE FROM attribut");
        jdbc.update("UPDATE categorie SET parent_id = NULL");
        jdbc.update("DELETE FROM categorie");
        jdbc.update("DELETE FROM marque");
        jdbc.update("DELETE FROM univers");
    }
}
