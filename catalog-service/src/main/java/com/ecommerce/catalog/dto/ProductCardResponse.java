package com.ecommerce.catalog.dto;

import com.ecommerce.catalog.entity.Product;
import com.ecommerce.catalog.entity.Variant;
import com.ecommerce.catalog.entity.VariantImage;
import com.ecommerce.catalog.support.Prices;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Objects;

/**
 * A product as a card in a list (C02): enough for the storefront grid,
 * nothing more. Prices include VAT and come from the cheapest variant,
 * an "à partir de" price.
 *
 * @param regularPriceInclTax the struck-through price when the cheapest variant is on sale, or null
 * @param available           at least one variant is in stock
 * @param imageUrl            the main photo of the cheapest variant, or of any variant, or null
 */
public record ProductCardResponse(
        Long id,
        String slug,
        String title,
        ReferenceView brand,
        ReferenceView category,
        BigDecimal priceInclTax,
        BigDecimal regularPriceInclTax,
        String imageUrl,
        boolean available,
        int variantCount) {

    /** A brand, category or universe as a product shows it. */
    public record ReferenceView(String name, String slug) {
    }

    public static ProductCardResponse from(Product product) {
        BigDecimal rate = product.getVatRate().getRate();
        Variant cheapest = product.getVariants().stream()
                .min(Comparator.comparing((Variant variant) -> Prices.currentInclTax(variant, rate))
                        .thenComparing(Variant::getId))
                .orElseThrow(() -> new IllegalStateException("Product " + product.getSlug() + " has no variant"));
        String imageUrl = firstImage(cheapest);
        if (imageUrl == null) {
            imageUrl = product.getVariants().stream().map(ProductCardResponse::firstImage)
                    .filter(Objects::nonNull).findFirst().orElse(null);
        }
        return new ProductCardResponse(
                product.getId(),
                product.getSlug(),
                product.getTitle(),
                new ReferenceView(product.getBrand().getName(), product.getBrand().getSlug()),
                new ReferenceView(product.getCategory().getName(), product.getCategory().getSlug()),
                Prices.currentInclTax(cheapest, rate),
                Prices.regularInclTax(cheapest, rate),
                imageUrl,
                product.getVariants().stream().anyMatch(variant -> variant.getStock() > 0),
                product.getVariants().size());
    }

    private static String firstImage(Variant variant) {
        return variant.getImages().stream().findFirst().map(VariantImage::getUrl).orElse(null);
    }
}
