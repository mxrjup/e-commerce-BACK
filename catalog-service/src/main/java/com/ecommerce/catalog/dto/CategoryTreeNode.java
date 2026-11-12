package com.ecommerce.catalog.dto;

import java.util.List;

/**
 * A category and its sub-categories, as the storefront menu displays them.
 */
public record CategoryTreeNode(Long id, String name, String slug, String description, int position,
                               List<CategoryTreeNode> children) {
}
