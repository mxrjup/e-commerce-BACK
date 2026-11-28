package com.ecommerce.catalog.dto;

import com.ecommerce.catalog.entity.Attribute;
import com.ecommerce.catalog.entity.AttributeValue;
import com.ecommerce.catalog.entity.Product;
import com.ecommerce.catalog.entity.ProductSpecification;
import com.ecommerce.catalog.entity.Universe;
import com.ecommerce.catalog.entity.Variant;
import com.ecommerce.catalog.entity.VariantImage;
import com.ecommerce.catalog.support.Prices;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A product page (C02): the sheet, the attributes to choose a variant, and
 * each variant with its price including VAT.
 *
 * <p>Neither the purchase price nor the exact stock appear: the storefront
 * only says whether a variant is available, and when it is running low.
 *
 * @param description HTML from the back-office editor
 * @param attributes  what the customer chooses, in a stable order: "Couleur" then "Layout clavier"
 */
public record ProductDetailResponse(
        Long id,
        String slug,
        String title,
        String description,
        BrandView brand,
        ProductCardResponse.ReferenceView category,
        List<ProductCardResponse.ReferenceView> universes,
        BigDecimal vatRate,
        List<SpecificationView> specifications,
        List<AttributeView> attributes,
        List<VariantView> variants) {

    public record BrandView(String name, String slug, String logoUrl) {
    }

    public record SpecificationView(String key, String value) {
    }

    /** An attribute and the values its variants use, in the order of the back-office. */
    public record AttributeView(String name, List<String> values) {
    }

    /**
     * @param values              attribute name to value: {"Couleur": "Noir", "Layout clavier": "AZERTY"}
     * @param regularPriceInclTax the struck-through price when on sale, or null
     * @param ecoTaxInclTax       the eco-tax share of the price, or null
     * @param lowStock            available, but at or below the alert threshold: "plus que quelques exemplaires"
     */
    public record VariantView(
            Long id,
            String sku,
            Map<String, String> values,
            BigDecimal priceInclTax,
            BigDecimal regularPriceInclTax,
            BigDecimal ecoTaxInclTax,
            boolean available,
            boolean lowStock,
            List<String> images) {
    }

    private static final Comparator<AttributeValue> BY_ATTRIBUTE = Comparator
            .comparing((AttributeValue value) -> value.getAttribute().getId())
            .thenComparing(AttributeValue::getPosition)
            .thenComparing(AttributeValue::getId);

    public static ProductDetailResponse from(Product product) {
        BigDecimal rate = product.getVatRate().getRate();
        return new ProductDetailResponse(
                product.getId(),
                product.getSlug(),
                product.getTitle(),
                product.getDescription(),
                new BrandView(product.getBrand().getName(), product.getBrand().getSlug(), product.getBrand().getLogoUrl()),
                new ProductCardResponse.ReferenceView(product.getCategory().getName(), product.getCategory().getSlug()),
                product.getUniverses().stream()
                        .sorted(Comparator.comparing(Universe::getPosition).thenComparing(Universe::getName))
                        .map(universe -> new ProductCardResponse.ReferenceView(universe.getName(), universe.getSlug()))
                        .toList(),
                rate,
                product.getSpecifications().stream()
                        .map((ProductSpecification spec) -> new SpecificationView(spec.getKey(), spec.getValue()))
                        .toList(),
                attributes(product),
                product.getVariants().stream().map(variant -> variant(variant, rate)).toList());
    }

    private static List<AttributeView> attributes(Product product) {
        Map<Attribute, List<String>> byAttribute = new LinkedHashMap<>();
        product.getVariants().stream()
                .flatMap(variant -> variant.getValues().stream())
                .distinct()
                .sorted(BY_ATTRIBUTE)
                .forEach(value -> byAttribute.computeIfAbsent(value.getAttribute(), key -> new ArrayList<>())
                        .add(value.getValue()));
        return byAttribute.entrySet().stream()
                .map(entry -> new AttributeView(entry.getKey().getName(), List.copyOf(entry.getValue())))
                .toList();
    }

    private static VariantView variant(Variant variant, BigDecimal rate) {
        Map<String, String> values = new LinkedHashMap<>();
        variant.getValues().stream().sorted(BY_ATTRIBUTE)
                .forEach(value -> values.put(value.getAttribute().getName(), value.getValue()));
        boolean available = variant.getStock() > 0;
        return new VariantView(
                variant.getId(),
                variant.getSku(),
                values,
                Prices.currentInclTax(variant, rate),
                Prices.regularInclTax(variant, rate),
                Prices.ecoTaxInclTax(variant, rate),
                available,
                available && variant.isLowOnStock(),
                variant.getImages().stream().map(VariantImage::getUrl).toList());
    }
}
