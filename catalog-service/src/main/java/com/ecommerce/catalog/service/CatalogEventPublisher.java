package com.ecommerce.catalog.service;

import com.ecommerce.catalog.entity.Product;
import com.ecommerce.catalog.entity.ProductStatus;
import com.ecommerce.catalog.entity.Variant;
import com.ecommerce.common.event.BackInStockEvent;
import com.ecommerce.common.event.CatalogEvent;
import com.ecommerce.common.event.PriceDroppedEvent;
import com.ecommerce.common.kafka.KafkaTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;

/**
 * Publishes the events of the catalogue on {@code catalogue.evenements} (C09),
 * for notification-service to warn the customers who follow a variant.
 *
 * <p>Only for a published product: nobody can follow a draft or an archived
 * one. An event leaves once the change is committed, never before: a change
 * that rolls back announces nothing.
 *
 * <p>Fire and forget: if the broker is down, the change stays committed and
 * the event is lost, with a WARN in the logs. A missed alert is better than a
 * price or a stock the back-office cannot change. The robust alternative, an
 * outbox table read by a relay, is described in the README.
 *
 * <p>The key is the variant id: the events of one variant land on the same
 * partition, and are read in the order they were published.
 */
@Component
public class CatalogEventPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topic;
    private final boolean enabled;

    public CatalogEventPublisher(KafkaTemplate<String, Object> kafkaTemplate,
                                 @Value("${app.kafka.topics.catalog-events:" + KafkaTopics.CATALOG_EVENTS + "}") String topic,
                                 @Value("${catalog.events.enabled:true}") boolean enabled) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.enabled = enabled;
    }

    /** When the price the customer pays went down; nothing when it went up or stayed. */
    public void priceChanged(Variant variant, BigDecimal previousUnitPriceExclTax) {
        BigDecimal now = variant.unitPriceExclTax();
        if (isOnSale(variant.getProduct()) && now.compareTo(previousUnitPriceExclTax) < 0) {
            publishAfterCommit(PriceDroppedEvent.builder()
                    .productId(variant.getProduct().getId())
                    .variantId(variant.getId())
                    .previousUnitPriceExclTax(previousUnitPriceExclTax)
                    .newUnitPriceExclTax(now)
                    .build());
        }
    }

    /**
     * When units were added to a variant that had none.
     *
     * @param stock the stock after the addition
     */
    public void stockAdded(Long productId, ProductStatus productStatus, Long variantId, int added, int stock) {
        if (productStatus == ProductStatus.PUBLIE && added > 0 && stock == added) {
            publishAfterCommit(BackInStockEvent.builder()
                    .productId(productId)
                    .variantId(variantId)
                    .availableQuantity(stock)
                    .build());
        }
    }

    private static boolean isOnSale(Product product) {
        return product.getStatus() == ProductStatus.PUBLIE;
    }

    private void publishAfterCommit(CatalogEvent event) {
        if (!enabled) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send(event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                send(event);
            }
        });
    }

    private void send(CatalogEvent event) {
        String kind = event.getClass().getSimpleName();
        try {
            kafkaTemplate.send(topic, String.valueOf(event.variantId()), event).whenComplete((result, error) -> {
                if (error != null) {
                    LOGGER.warn("{} lost for variant {}: {}", kind, event.variantId(), error.getMessage());
                } else {
                    LOGGER.info("{} published for variant {}", kind, event.variantId());
                }
            });
        } catch (RuntimeException e) {
            // Broker injoignable : send() echoue apres max.block.ms, la modification reste enregistree.
            LOGGER.warn("{} lost for variant {}: {}", kind, event.variantId(), e.getMessage());
        }
    }
}
