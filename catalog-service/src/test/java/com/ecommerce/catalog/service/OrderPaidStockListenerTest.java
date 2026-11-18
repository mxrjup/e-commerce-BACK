package com.ecommerce.catalog.service;

import com.ecommerce.catalog.demo.DemoCatalogLoader;
import com.ecommerce.catalog.repository.ProcessedOrderRepository;
import com.ecommerce.catalog.support.CatalogTestData;
import com.ecommerce.common.event.OrderPaidEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The paid-order consumer (C21), called directly over the demonstration
 * catalogue: what it takes out of the stock, and what it never does twice.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:catalogorderpaid;MODE=PostgreSQL;"
        + "DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("demo")
class OrderPaidStockListenerTest {

    @Autowired
    private OrderPaidStockListener listener;

    @Autowired
    private ProcessedOrderRepository processedOrderRepository;

    @Autowired
    private DemoCatalogLoader loader;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void loadIfEmpty() {
        loader.run(null);
    }

    @AfterEach
    void tearDown() {
        CatalogTestData.clear(jdbcTemplate);
        processedOrderRepository.deleteAll();
    }

    @Test
    void shouldTakeThePaidQuantitiesOutOfTheStock() {
        listener.onOrderPaid(order(1L, item("LOGI-G915TKL-AZ-NOI", 2), item("LOGI-MXM3S-GRI", 1)));

        assertThat(stockOf("LOGI-G915TKL-AZ-NOI")).isEqualTo(6);
        assertThat(stockOf("LOGI-MXM3S-GRI")).isEqualTo(39);
        assertThat(processedOrderRepository.findById(1L)).get()
                .extracting(order -> order.getOrderNumber()).isEqualTo("CMD-2026-000001");
    }

    @Test
    void shouldNeverTakeTheSameOrderTwice() {
        OrderPaidEvent paid = order(2L, item("LOGI-G915TKL-AZ-NOI", 2));
        listener.onOrderPaid(paid);
        // Meme commande, nouvel eventId : le webhook Stripe rappele.
        listener.onOrderPaid(order(2L, item("LOGI-G915TKL-AZ-NOI", 2)));

        assertThat(stockOf("LOGI-G915TKL-AZ-NOI")).isEqualTo(6);
    }

    @Test
    void shouldTakeWhatRemainsWhenTheStockFallsShort() {
        // LG UltraGear : 2 en stock, 3 payes.
        listener.onOrderPaid(order(3L, item("LG-27GR95QE", 3), item("DELL-U2723QE", 1)));

        assertThat(stockOf("LG-27GR95QE")).isZero();
        assertThat(stockOf("DELL-U2723QE")).isEqualTo(6);
        assertThat(processedOrderRepository.existsById(3L)).isTrue();
    }

    @Test
    void shouldServeTheOtherLinesWhenAVariantNoLongerExists() {
        listener.onOrderPaid(order(4L,
                new OrderPaidEvent.Item(999_999L, 1), item("SAMS-990PRO-1T", 4)));

        assertThat(stockOf("SAMS-990PRO-1T")).isEqualTo(46);
        assertThat(processedOrderRepository.existsById(4L)).isTrue();
    }

    @Test
    void shouldAddUpTwoLinesOfTheSameVariant() {
        listener.onOrderPaid(order(5L, item("SAMS-990PRO-2T", 2), item("SAMS-990PRO-2T", 3)));

        assertThat(stockOf("SAMS-990PRO-2T")).isEqualTo(15);
    }

    @Test
    void shouldRecordAnOrderWithoutLinesWithoutTouchingTheStock() {
        listener.onOrderPaid(OrderPaidEvent.builder().orderId(6L).orderNumber("CMD-2026-000006").build());

        assertThat(processedOrderRepository.existsById(6L)).isTrue();
        assertThat(stockOf("LOGI-G915TKL-AZ-NOI")).isEqualTo(8);
    }

    private OrderPaidEvent order(long id, OrderPaidEvent.Item... items) {
        return OrderPaidEvent.builder()
                .orderId(id)
                .orderNumber(String.format("CMD-2026-%06d", id))
                .userId(1L)
                .items(List.of(items))
                .build();
    }

    private OrderPaidEvent.Item item(String sku, int quantity) {
        return new OrderPaidEvent.Item(idOf(sku), quantity);
    }

    private long idOf(String sku) {
        return jdbcTemplate.queryForObject("SELECT id FROM variante WHERE sku = ?", Long.class, sku);
    }

    private int stockOf(String sku) {
        return jdbcTemplate.queryForObject("SELECT stock FROM variante WHERE sku = ?", Integer.class, sku);
    }
}
