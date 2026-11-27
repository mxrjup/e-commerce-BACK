package com.ecommerce.catalog.kafka;

import com.ecommerce.catalog.demo.DemoCatalogLoader;
import com.ecommerce.catalog.dto.AdminVariantRequest;
import com.ecommerce.catalog.entity.AttributeValue;
import com.ecommerce.catalog.entity.Variant;
import com.ecommerce.catalog.repository.VariantRepository;
import com.ecommerce.catalog.service.AdminVariantService;
import com.ecommerce.catalog.service.StockService;
import com.ecommerce.catalog.support.CatalogTestData;
import com.ecommerce.common.event.BackInStockEvent;
import com.ecommerce.common.event.CatalogEvent;
import com.ecommerce.common.event.PriceDroppedEvent;
import com.ecommerce.common.kafka.KafkaTopics;
import com.ecommerce.test.kafka.KafkaIntegrationTest;
import com.ecommerce.test.kafka.KafkaTestRecords;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The events of the catalogue (C09) on a real broker: the record lands on
 * {@code catalogue.evenements}, keyed by the variant, with the type header
 * that lets notification-service read it as a {@link CatalogEvent}.
 */
@ActiveProfiles("demo")
class CatalogEventsKafkaIntegrationTest extends KafkaIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @DynamicPropertySource
    static void events(DynamicPropertyRegistry registry) {
        registry.add("catalog.events.enabled", () -> "true");
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:catalogeventskafka;MODE=PostgreSQL;"
                + "DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
    }

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

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void loadIfEmpty() {
        loader.run(null);
    }

    @AfterEach
    void tearDown() {
        CatalogTestData.clear(jdbcTemplate);
    }

    @Test
    void shouldPublishAPriceDrop() throws Exception {
        String sku = "LOGI-G915TKL-AZ-NOI";
        long black = idOf(sku);

        variantService.update(black, transactionTemplate.execute(status -> {
            Variant variant = variantRepository.findBySku(sku).orElseThrow();
            return new AdminVariantRequest(sku, variant.getPurchasePriceExclTax(), variant.getSalePriceExclTax(),
                    new BigDecimal("149.99"), variant.getEcoTax(), variant.getWeightKg(),
                    variant.getValues().stream().map(AttributeValue::getId).toList(), null, null);
        }));

        ConsumerRecord<String, byte[]> record = KafkaTestRecords.awaitRecord(
                KafkaTopics.CATALOG_EVENTS, String.valueOf(black), TIMEOUT);
        assertThat(KafkaTestRecords.header(record, "__TypeId__")).isEqualTo(PriceDroppedEvent.class.getName());
        PriceDroppedEvent drop = objectMapper.readValue(record.value(), PriceDroppedEvent.class);
        assertThat(drop.previousUnitPriceExclTax()).isEqualByComparingTo("166.76");
        assertThat(drop.newUnitPriceExclTax()).isEqualByComparingTo("150.09");
    }

    @Test
    void shouldPublishAVariantBackInStock() throws Exception {
        long k70 = idOf("CORS-K70PRO-AZ-NOI");

        stockService.adjust(k70, 7);

        ConsumerRecord<String, byte[]> record = KafkaTestRecords.awaitRecord(
                KafkaTopics.CATALOG_EVENTS, String.valueOf(k70), TIMEOUT);
        assertThat(KafkaTestRecords.header(record, "__TypeId__")).isEqualTo(BackInStockEvent.class.getName());
        BackInStockEvent restock = objectMapper.readValue(record.value(), BackInStockEvent.class);
        assertThat(restock.variantId()).isEqualTo(k70);
        assertThat(restock.availableQuantity()).isEqualTo(7);
    }

    private long idOf(String sku) {
        return jdbcTemplate.queryForObject("SELECT id FROM variante WHERE sku = ?", Long.class, sku);
    }
}
