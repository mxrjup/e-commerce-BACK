package com.ecommerce.common.event;

/**
 * Events published on {@code catalogue.evenements}. The topic carries several
 * kinds of events; the concrete class travels in the record headers, so a
 * single listener receives them all and switches on the type:
 *
 * <pre>{@code
 * @KafkaListener(topics = "${app.kafka.topics.catalog-events}")
 * void on(CatalogEvent event) {
 *     switch (event) {
 *         case PriceDroppedEvent drop -> alerts.priceDropped(drop);
 *         case BackInStockEvent restock -> alerts.backInStock(restock);
 *     }
 * }
 * }</pre>
 *
 * The interface is sealed: adding a kind of event breaks the compilation of
 * every exhaustive {@code switch}, which is the point.
 */
public sealed interface CatalogEvent extends DomainEvent
        permits PriceDroppedEvent, BackInStockEvent {

    Long productId();

    Long variantId();
}
