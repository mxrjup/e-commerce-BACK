package com.ecommerce.catalog.support;

import com.ecommerce.catalog.entity.AttributeValue;
import com.ecommerce.catalog.entity.Variant;

import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * The name of a variant as people read it: the product title, then its
 * values in the order their attributes were created, so it never changes
 * between calls. "Logitech G915 TKL - Noir, AZERTY".
 */
public final class Designations {

    private Designations() {
    }

    public static String of(Variant variant) {
        String values = variant.getValues().stream()
                .sorted(Comparator.comparing((AttributeValue value) -> value.getAttribute().getId())
                        .thenComparing(AttributeValue::getId))
                .map(AttributeValue::getValue)
                .collect(Collectors.joining(", "));
        String title = variant.getProduct().getTitle();
        return values.isEmpty() ? title : title + " - " + values;
    }
}
