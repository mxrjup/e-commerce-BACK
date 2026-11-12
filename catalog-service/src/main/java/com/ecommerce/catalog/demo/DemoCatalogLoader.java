package com.ecommerce.catalog.demo;

import com.ecommerce.catalog.entity.Attribute;
import com.ecommerce.catalog.entity.AttributeValue;
import com.ecommerce.catalog.entity.Brand;
import com.ecommerce.catalog.entity.Category;
import com.ecommerce.catalog.entity.Product;
import com.ecommerce.catalog.entity.ProductStatus;
import com.ecommerce.catalog.entity.Universe;
import com.ecommerce.catalog.entity.Variant;
import com.ecommerce.catalog.entity.VatRate;
import com.ecommerce.catalog.repository.AttributeRepository;
import com.ecommerce.catalog.repository.BrandRepository;
import com.ecommerce.catalog.repository.CategoryRepository;
import com.ecommerce.catalog.repository.ProductRepository;
import com.ecommerce.catalog.repository.UniverseRepository;
import com.ecommerce.catalog.repository.VatRateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fills an empty catalogue with the demonstration data of
 * {@code demo/catalogue-demo.json}: brands, categories, universes,
 * attributes, then products and their variants.
 *
 * <p>Only with the {@code demo} profile (local runs and the Docker Compose
 * stack), never in production. It does nothing once the catalogue has a
 * product, so restarting the service never duplicates anything. Reference
 * entries that already exist (same slug or name) are reused, not recreated.
 */
@Component
@Profile("demo")
@RequiredArgsConstructor
public class DemoCatalogLoader implements ApplicationRunner {

    static final String RESOURCE = "demo/catalogue-demo.json";

    private static final Logger LOGGER = LoggerFactory.getLogger(DemoCatalogLoader.class);

    private final ObjectMapper objectMapper;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;
    private final UniverseRepository universeRepository;
    private final VatRateRepository vatRateRepository;
    private final AttributeRepository attributeRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (productRepository.count() > 0) {
            LOGGER.info("Catalogue already has products: demonstration data not loaded");
            return;
        }
        DemoCatalog catalog = read();

        Map<String, Brand> brands = new HashMap<>();
        catalog.brands().forEach(brand -> brands.put(brand.slug(), brand(brand)));

        Map<String, Category> categories = new HashMap<>();
        categories(catalog.categories(), null, categories);

        Map<String, Universe> universes = new HashMap<>();
        catalog.universes().forEach(universe -> universes.put(universe.slug(), universe(universe)));

        Map<String, Map<String, AttributeValue>> attributes = new HashMap<>();
        catalog.attributes().forEach(attribute -> attributes.put(attribute.name(), attribute(attribute)));

        for (DemoProduct demo : catalog.products()) {
            productRepository.save(product(demo, brands, categories, universes, attributes));
        }
        LOGGER.info("Demonstration catalogue loaded: {} products", catalog.products().size());
    }

    private DemoCatalog read() {
        try (InputStream input = new ClassPathResource(RESOURCE).getInputStream()) {
            return objectMapper.readValue(input, DemoCatalog.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + RESOURCE, e);
        }
    }

    private Brand brand(DemoBrand demo) {
        return brandRepository.findBySlug(demo.slug()).orElseGet(() -> {
            Brand brand = new Brand();
            brand.setName(demo.name());
            brand.setSlug(demo.slug());
            return brandRepository.save(brand);
        });
    }

    /** The tree in file order: a category's position is its rank among its siblings. */
    private void categories(List<DemoCategory> siblings, Category parent, Map<String, Category> bySlug) {
        for (int position = 0; position < siblings.size(); position++) {
            DemoCategory demo = siblings.get(position);
            int rank = position;
            Category category = categoryRepository.findBySlug(demo.slug()).orElseGet(() -> {
                Category created = new Category();
                created.setName(demo.name());
                created.setSlug(demo.slug());
                created.setParent(parent);
                created.setPosition(rank);
                return categoryRepository.save(created);
            });
            bySlug.put(demo.slug(), category);
            if (demo.children() != null) {
                categories(demo.children(), category, bySlug);
            }
        }
    }

    private Universe universe(DemoUniverse demo) {
        return universeRepository.findBySlug(demo.slug()).orElseGet(() -> {
            Universe universe = new Universe();
            universe.setName(demo.name());
            universe.setSlug(demo.slug());
            universe.setDescription(demo.description());
            universe.setPosition(demo.position());
            return universeRepository.save(universe);
        });
    }

    /** The attribute, created if needed with its missing values, as a value lookup. */
    private Map<String, AttributeValue> attribute(DemoAttribute demo) {
        Attribute attribute = attributeRepository.findByNameIgnoreCase(demo.name()).orElseGet(() -> {
            Attribute created = new Attribute();
            created.setName(demo.name());
            return created;
        });
        for (String value : demo.values()) {
            boolean known = attribute.getValues().stream().anyMatch(existing -> existing.getValue().equals(value));
            if (!known) {
                attribute.addValue(value);
            }
        }
        Attribute saved = attributeRepository.save(attribute);
        Map<String, AttributeValue> byValue = new HashMap<>();
        saved.getValues().forEach(value -> byValue.put(value.getValue(), value));
        return byValue;
    }

    private Product product(DemoProduct demo, Map<String, Brand> brands, Map<String, Category> categories,
                            Map<String, Universe> universes, Map<String, Map<String, AttributeValue>> attributes) {
        Product product = new Product();
        product.setTitle(demo.title());
        product.setSlug(demo.slug());
        product.setDescription(demo.description());
        product.setStatus(demo.status());
        product.setBrand(required(brands, demo.brand(), "brand", demo));
        product.setCategory(required(categories, demo.category(), "category", demo));
        product.setVatRate(vatRate(demo.vat()));
        demo.universes().forEach(slug -> product.getUniverses().add(required(universes, slug, "universe", demo)));
        demo.specifications().forEach(product::addSpecification);

        for (DemoVariant demoVariant : demo.variants()) {
            Variant variant = new Variant();
            variant.setSku(demoVariant.sku());
            variant.setPurchasePriceExclTax(demoVariant.purchasePrice());
            variant.setSalePriceExclTax(demoVariant.salePrice());
            variant.setDiscountPriceExclTax(demoVariant.discountPrice());
            variant.setEcoTax(demoVariant.ecoTax());
            variant.setWeightKg(demoVariant.weightKg());
            variant.setStock(demoVariant.stock());
            variant.setAlertThreshold(demoVariant.alertThreshold());
            demoVariant.values().forEach((attribute, value) ->
                    variant.getValues().add(required(required(attributes, attribute, "attribute", demo),
                            value, "value of " + attribute, demo)));
            product.addVariant(variant);
        }
        return product;
    }

    private VatRate vatRate(String label) {
        return vatRateRepository.findByLabelIgnoreCase(label)
                .orElseThrow(() -> new IllegalStateException("Demo data refers to an unknown VAT rate '" + label + "'"));
    }

    private static <T> T required(Map<String, T> known, String key, String what, DemoProduct product) {
        T value = known.get(key);
        if (value == null) {
            throw new IllegalStateException("Demo product '" + product.slug() + "' refers to an unknown "
                    + what + " '" + key + "'");
        }
        return value;
    }

    record DemoCatalog(List<DemoBrand> brands, List<DemoCategory> categories, List<DemoUniverse> universes,
                       List<DemoAttribute> attributes, List<DemoProduct> products) {
    }

    record DemoBrand(String name, String slug) {
    }

    record DemoCategory(String name, String slug, List<DemoCategory> children) {
    }

    record DemoUniverse(String name, String slug, String description, int position) {
    }

    record DemoAttribute(String name, List<String> values) {
    }

    record DemoProduct(String title, String slug, String brand, String category, String vat, ProductStatus status,
                       List<String> universes, String description, Map<String, String> specifications,
                       List<DemoVariant> variants) {
    }

    record DemoVariant(String sku, BigDecimal purchasePrice, BigDecimal salePrice, BigDecimal discountPrice,
                       BigDecimal ecoTax, BigDecimal weightKg, int stock, int alertThreshold,
                       Map<String, String> values) {
    }
}
