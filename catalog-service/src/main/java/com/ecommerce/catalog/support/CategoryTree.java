package com.ecommerce.catalog.support;

import com.ecommerce.catalog.entity.Category;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Filtering on a category includes its sub-categories: "Périphériques"
 * finds the keyboards. Shared by the storefront (C02) and the back-office
 * product list (C04).
 */
public final class CategoryTree {

    private CategoryTree() {
    }

    /** The id of the root and of every category below it, from the whole list of categories. */
    public static Set<Long> withDescendants(List<Category> all, Long rootId) {
        Map<Long, List<Long>> children = new HashMap<>();
        for (Category category : all) {
            if (category.getParentId() != null) {
                children.computeIfAbsent(category.getParentId(), key -> new ArrayList<>()).add(category.getId());
            }
        }
        Set<Long> ids = new LinkedHashSet<>();
        Deque<Long> toVisit = new ArrayDeque<>(List.of(rootId));
        while (!toVisit.isEmpty()) {
            Long id = toVisit.pop();
            if (ids.add(id)) {
                toVisit.addAll(children.getOrDefault(id, List.of()));
            }
        }
        return ids;
    }
}
