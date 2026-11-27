package com.ecommerce.catalog.service;

import com.ecommerce.catalog.demo.DemoCatalogLoader;
import com.ecommerce.catalog.dto.AdminVariantRequest;
import com.ecommerce.catalog.entity.AttributeValue;
import com.ecommerce.catalog.entity.Variant;
import com.ecommerce.catalog.repository.VariantRepository;
import com.ecommerce.catalog.support.CatalogTestData;
import com.ecommerce.common.event.BackInStockEvent;
import com.ecommerce.common.event.PriceDroppedEvent;
import com.ecommerce.common.kafka.KafkaTopics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The events of the catalogue (C09) over the demonstration catalogue, the
 * broker replaced by a mock: which changes are announced, and when.
 * {@code CatalogEventsKafkaIntegrationTest} checks the records on a real one.
 */
@SpringBootTest(properties = {
        "catalog.events.enabled=true",
        "spring.datasource.url=jdbc:h2:mem:catalogevents;MODE=PostgreSQL;"
                + "DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"})
@ActiveProfiles("demo")
class CatalogEventsTest {

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private AdminVariantService variantService;

    @Autowired
    private StockService stockService;

    @Autowired
    private VariantRepository variantRepository;

    @Autowired
    private DemoCatalogLoader loader;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        loader.run(null);
        CompletableFuture<SendResult<String, Object>> sent = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(sent);
    }

    @AfterEach
    void tearDown() {
        CatalogTestData.clear(jdbcTemplate);
    }

    @Test
    void shouldAnnounceALowerPriceOnceSaved() {
        // G915 noir : remise a 166,66 + 0,10 d'ecotaxe, publie.
        long black = idOf("LOGI-G915TKL-AZ-NOI");

        variantService.update(black, edited("LOGI-G915TKL-AZ-NOI", "191.66", "149.99", "0.10"));

        PriceDroppedEvent drop = (PriceDroppedEvent) sentFor(black);
        assertThat(drop.productId()).isEqualTo(productOf("LOGI-G915TKL-AZ-NOI"));
        assertThat(drop.previousUnitPriceExclTax()).isEqualByComparingTo("166.76");
        assertThat(drop.newUnitPriceExclTax()).isEqualByComparingTo("150.09");
    }

    @Test
    void shouldCountTheEcoTaxInThePrice() {
        long black = idOf("LOGI-G915TKL-AZ-NOI");

        variantService.update(black, edited("LOGI-G915TKL-AZ-NOI", "191.66", "166.66", null));

        PriceDroppedEvent drop = (PriceDroppedEvent) sentFor(black);
        assertThat(drop.newUnitPriceExclTax()).isEqualByComparingTo("166.66");
    }

    @Test
    void shouldAnnounceNothingWhenThePriceRisesOrStays() {
        long black = idOf("LOGI-G915TKL-AZ-NOI");

        // Le prix barre baisse, sous une remise inchangee : le client paie pareil.
        variantService.update(black, edited("LOGI-G915TKL-AZ-NOI", "180.00", "166.66", "0.10"));
        // La remise retiree : le client paie plus.
        variantService.update(black, edited("LOGI-G915TKL-AZ-NOI", "180.00", null, "0.10"));

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void shouldAnnounceNothingForAProductCustomersCannotSee() {
        // Brouillon, puis archive.
        variantService.update(idOf("CORS-VENG-DDR5-32"), edited("CORS-VENG-DDR5-32", "99.00", null, "0.02"));
        stockService.adjust(idOf("LOGI-MXKEYS-AZ-GRI"), 5);

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void shouldAnnounceNothingWhenTheChangeRollsBack() {
        long black = idOf("LOGI-G915TKL-AZ-NOI");

        transactionTemplate.executeWithoutResult(status -> {
            variantService.update(black, edited("LOGI-G915TKL-AZ-NOI", "191.66", "99.00", "0.10"));
            stockService.adjust(idOf("CORS-K70PRO-AZ-NOI"), 4);
            status.setRollbackOnly();
        });

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
        assertThat(stockOf("CORS-K70PRO-AZ-NOI")).isZero();
    }

    @Test
    void shouldAnnounceAVariantBackInStock() {
        // K70 : publie, en rupture.
        long k70 = idOf("CORS-K70PRO-AZ-NOI");

        stockService.adjust(k70, 5);

        BackInStockEvent restock = (BackInStockEvent) sentFor(k70);
        assertThat(restock.productId()).isEqualTo(productOf("CORS-K70PRO-AZ-NOI"));
        assertThat(restock.availableQuantity()).isEqualTo(5);
    }

    @Test
    void shouldAnnounceNothingWhenTheVariantWasNotOutOfStock() {
        // G915 blanc : 2 en stock.
        long white = idOf("LOGI-G915TKL-AZ-BLA");

        stockService.adjust(white, 10);
        stockService.adjust(white, -12);

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
        assertThat(stockOf("LOGI-G915TKL-AZ-BLA")).isZero();
    }

    @Test
    void shouldKeepTheChangeWhenTheBrokerIsDown() {
        long k70 = idOf("CORS-K70PRO-AZ-NOI");
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenThrow(new IllegalStateException("broker down"));

        assertThat(stockService.adjust(k70, 3).stock()).isEqualTo(3);

        assertThat(stockOf("CORS-K70PRO-AZ-NOI")).isEqualTo(3);
    }

    /** The one event sent, checked to be on the catalogue topic with the variant id as key. */
    private Object sentFor(long variantId) {
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.CATALOG_EVENTS), eq(String.valueOf(variantId)), event.capture());
        return event.getValue();
    }

    /** The current variant, with these prices; null for no discount or no eco-tax. */
    private AdminVariantRequest edited(String sku, String sale, String discount, String ecoTax) {
        return transactionTemplate.execute(status -> {
            Variant variant = variantRepository.findBySku(sku).orElseThrow();
            return new AdminVariantRequest(sku, variant.getPurchasePriceExclTax(), new BigDecimal(sale),
                    discount == null ? null : new BigDecimal(discount), ecoTax == null ? null : new BigDecimal(ecoTax),
                    variant.getWeightKg(), variant.getValues().stream().map(AttributeValue::getId).toList(),
                    null, null);
        });
    }

    private long idOf(String sku) {
        return jdbcTemplate.queryForObject("SELECT id FROM variante WHERE sku = ?", Long.class, sku);
    }

    private long productOf(String sku) {
        return jdbcTemplate.queryForObject("SELECT produit_id FROM variante WHERE sku = ?", Long.class, sku);
    }

    private int stockOf(String sku) {
        return jdbcTemplate.queryForObject("SELECT stock FROM variante WHERE sku = ?", Integer.class, sku);
    }
}
