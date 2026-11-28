package com.ecommerce.catalog.support;

import java.util.Locale;

/**
 * The LIKE pattern of a back-office search box, for the inventory (C07) and
 * the product list (C04). The queries declare {@code !} as escape character.
 */
public final class LikePatterns {

    private LikePatterns() {
    }

    /** Lowercase, with the user's text taken literally: % and _ match themselves. */
    public static String contains(String search) {
        if (search == null || search.isBlank()) {
            return "%";
        }
        String escaped = search.trim().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }
}
