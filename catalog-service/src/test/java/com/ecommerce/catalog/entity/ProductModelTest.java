package com.ecommerce.catalog.entity;

import com.ecommerce.catalog.repository.AttributeRepository;
import com.ecommerce.catalog.repository.BrandRepository;
import com.ecommerce.catalog.repository.CategoryRepository;
import com.ecommerce.catalog.repository.ProductRepository;
import com.ecommerce.catalog.repository.UniverseRepository;
import com.ecommerce.catalog.repository.VariantRepository;
import com.ecommerce.catalog.repository.VatRateRepository;
import com.ecommerce.catalog.service.BrandService;
import com.ecommerce.catalog.support.CatalogTestData;
import com.ecommerce.common.exception.ConflictException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The product model against the schema of the real migrations: what is
 * saved comes back, and the constraints refuse what must never be stored.
 */
@SpringBootTest
class ProductModelTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private VariantRepository variantRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UniverseRepository universeRepository;

    @Autowired
    private VatRateRepository vatRateRepository;

    @Autowired
    private AttributeRepository attributeRepository;

    @Autowired
    private BrandService brandService;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Brand brand;
    private Category category;
    private Universe universe;
    private AttributeValue black;
    private AttributeValue azerty;

    @BeforeEach
    void setUp() {
        CatalogTestData.clear(jdbcTemplate);
        brand = brandRepository.save(brand("Logitech", "logitech"));
        category = categoryRepository.save(category("Claviers", "claviers"));
        universe = universeRepository.save(universe("Télétravail", "teletravail"));

        Attribute color = new Attribute();
        color.setName("Couleur");
        color.addValue("Noir");
        Attribute layout = new Attribute();
        layout.setName("Layout clavier");
        layout.addValue("AZERTY");
        black = attributeRepository.save(color).getValues().get(0);
        azerty = attributeRepository.save(layout).getValues().get(0);
    }

    @AfterEach
    void tearDown() {
        CatalogTestData.clear(jdbcTemplate);
    }

    @Test
    void shouldSaveAProductWithItsVariantsAndReadItBack() {
        Long id = transaction.execute(status -> {
            Product product = product("logitech-mx-keys-s");
            product.getUniverses().add(universe);
            product.addSpecification("Garantie", "2 ans");
            product.addSpecification("Connectique", "Bluetooth");
            Variant variant = product.addVariant(variant("LOGI-MXKEYS-S-AZ-NOI", "99.99", 25));
            variant.setDiscountPriceExclTax(new BigDecimal("83.33"));
            variant.setEcoTax(new BigDecimal("0.10"));
            variant.getValues().add(black);
            variant.getValues().add(azerty);
            variant.addImage("/images/mx-keys-s-noir-1.jpg");
            variant.addImage("/images/mx-keys-s-noir-2.jpg");
            return productRepository.save(product).getId();
        });

        transaction.executeWithoutResult(status -> {
            Product saved = productRepository.findById(id).orElseThrow();
            assertThat(saved.getStatus()).isEqualTo(ProductStatus.BROUILLON);
            assertThat(saved.getBrand().getSlug()).isEqualTo("logitech");
            assertThat(saved.getVatRate().getLabel()).isEqualTo("Taux normal");
            assertThat(saved.getUniverses()).extracting(Universe::getSlug).containsExactly("teletravail");
            assertThat(saved.getSpecifications()).extracting(ProductSpecification::getKey)
                    .containsExactly("Garantie", "Connectique");

            Variant variant = saved.getVariants().get(0);
            assertThat(variant.getSalePriceExclTax()).isEqualByComparingTo("99.99");
            assertThat(variant.currentPriceExclTax()).isEqualByComparingTo("83.33");
            assertThat(variant.getValues()).extracting(AttributeValue::getValue)
                    .containsExactlyInAnyOrder("Noir", "AZERTY");
            assertThat(variant.getImages()).extracting(VariantImage::getPosition).containsExactly(1, 2);
            assertThat(saved.getCreatedAt()).isNotNull();
        });
    }

    @Test
    void shouldRefuseANegativeStock() {
        Product product = product("stock-negatif");
        product.addVariant(variant("SKU-NEG", "10.00", -1));

        assertThatThrownBy(() -> productRepository.saveAndFlush(product))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRefuseADiscountThatIsNotBelowThePrice() {
        Product product = product("remise-trop-haute");
        Variant variant = product.addVariant(variant("SKU-PROMO", "10.00", 1));
        variant.setDiscountPriceExclTax(new BigDecimal("10.00"));

        assertThatThrownBy(() -> productRepository.saveAndFlush(product))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRefuseTwoVariantsWithTheSameSku() {
        Product product = product("sku-en-double");
        product.addVariant(variant("SKU-DOUBLE", "10.00", 1));
        product.addVariant(variant("SKU-DOUBLE", "12.00", 1));

        assertThatThrownBy(() -> productRepository.saveAndFlush(product))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldDeleteTheVariantsWithTheirProduct() {
        Product product = product("a-supprimer");
        product.addVariant(variant("SKU-SUPPR", "10.00", 1));
        Long id = productRepository.saveAndFlush(product).getId();

        productRepository.deleteById(id);

        assertThat(variantRepository.findBySku("SKU-SUPPR")).isEmpty();
    }

    @Test
    void shouldKeepABrandThatProductsStillUse() {
        Product product = product("marque-utilisee");
        product.addVariant(variant("SKU-MARQUE", "10.00", 1));
        productRepository.saveAndFlush(product);

        assertThatThrownBy(() -> brandService.delete(brand.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("still used");
    }

    @Test
    void shouldFlagAVariantAtOrBelowItsAlertThreshold() {
        Variant variant = variant("SKU-SEUIL", "10.00", 3);
        variant.setAlertThreshold(3);
        assertThat(variant.isLowOnStock()).isTrue();

        variant.setStock(4);
        assertThat(variant.isLowOnStock()).isFalse();
    }

    private Product product(String slug) {
        Product product = new Product();
        product.setTitle("Produit " + slug);
        product.setSlug(slug);
        product.setBrand(brand);
        product.setCategory(category);
        product.setVatRate(vatRateRepository.findByLabelIgnoreCase("Taux normal").orElseThrow());
        return product;
    }

    private static Variant variant(String sku, String price, int stock) {
        Variant variant = new Variant();
        variant.setSku(sku);
        variant.setPurchasePriceExclTax(new BigDecimal("5.00"));
        variant.setSalePriceExclTax(new BigDecimal(price));
        variant.setWeightKg(new BigDecimal("0.810"));
        variant.setStock(stock);
        return variant;
    }

    private static Brand brand(String name, String slug) {
        Brand brand = new Brand();
        brand.setName(name);
        brand.setSlug(slug);
        return brand;
    }

    private static Category category(String name, String slug) {
        Category category = new Category();
        category.setName(name);
        category.setSlug(slug);
        return category;
    }

    private static Universe universe(String name, String slug) {
        Universe universe = new Universe();
        universe.setName(name);
        universe.setSlug(slug);
        return universe;
    }
}
