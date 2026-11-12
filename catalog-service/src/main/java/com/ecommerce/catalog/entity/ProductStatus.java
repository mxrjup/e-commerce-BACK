package com.ecommerce.catalog.entity;

/**
 * Where a product stands in the back-office workflow. Only a published
 * product is visible on the storefront.
 */
public enum ProductStatus {
    BROUILLON,
    PUBLIE,
    ARCHIVE
}
