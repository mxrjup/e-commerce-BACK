package com.ecommerce.catalog.service;

import com.ecommerce.catalog.entity.ProcessedOrder;
import com.ecommerce.catalog.repository.ProcessedOrderRepository;
import com.ecommerce.common.event.OrderPaidEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Takes the quantities of a paid order out of the stock (C21).
 *
 * <p>Idempotent: Kafka delivers at least once, and the same order can be
 * published twice (Stripe calling the webhook again). The order id is
 * recorded in the same transaction as the stock change, so a second delivery
 * finds it and changes nothing. If anything fails, both roll back together,
 * and the shared policy of common-lib retries then sends the record to
 * {@code order.paid.dlt}.
 */
@Component
@RequiredArgsConstructor
public class OrderPaidStockListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderPaidStockListener.class);

    private final StockService stockService;
    private final ProcessedOrderRepository processedOrderRepository;

    @KafkaListener(topics = "${app.kafka.topics.order-paid:order.paid}")
    @Transactional
    public void onOrderPaid(OrderPaidEvent event) {
        if (processedOrderRepository.existsById(event.orderId())) {
            LOGGER.info("Order {} already taken out of the stock, ignored", event.orderNumber());
            return;
        }
        if (event.items().isEmpty()) {
            // Producteur anterieur a C21 : rien a retirer, mais il faut le voir.
            LOGGER.error("Order {} paid without its lines: the stock was NOT updated", event.orderNumber());
        }

        List<StockService.Shortage> shortages = stockService.takePaidOrder(quantities(event));
        for (StockService.Shortage shortage : shortages) {
            if (shortage.unknownVariant()) {
                LOGGER.warn("Order {} paid for {} unit(s) of variant {}, which no longer exists in the catalogue",
                        event.orderNumber(), shortage.ordered(), shortage.variantId());
            } else {
                LOGGER.warn("Order {} paid for {} unit(s) of variant {} but only {} were in stock: {} missing,"
                                + " the customer has to be contacted", event.orderNumber(), shortage.ordered(),
                        shortage.variantId(), shortage.taken(), shortage.missing());
            }
        }

        processedOrderRepository.save(new ProcessedOrder(event.orderId(), event.orderNumber(), Instant.now()));
        LOGGER.info("Order {} taken out of the stock ({} line(s), {} short)",
                event.orderNumber(), event.items().size(), shortages.size());
    }

    /** One entry per variant: two lines of the same variant add up. */
    private static Map<Long, Integer> quantities(OrderPaidEvent event) {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (OrderPaidEvent.Item item : event.items()) {
            if (item.variantId() != null) {
                quantities.merge(item.variantId(), item.quantity(), Integer::sum);
            }
        }
        return quantities;
    }
}
