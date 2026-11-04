package com.ecommerce.common.kafka;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import com.ecommerce.common.event.BackInStockEvent;
import com.ecommerce.common.event.CatalogEvent;
import com.ecommerce.common.event.OrderPaidEvent;
import com.ecommerce.common.event.PriceDroppedEvent;
import com.ecommerce.common.event.SupportAdminReplyEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The events cross the wire with the serializers configured by
 * kafka-defaults.properties: JSON body, class name in the headers.
 */
class SharedEventSerializationTest {

    private static final String TOPIC = "any.topic";

    private final JsonSerializer<Object> serializer = new JsonSerializer<>();
    private final JsonDeserializer<Object> deserializer = new JsonDeserializer<>();

    SharedEventSerializationTest() {
        deserializer.configure(Map.of(JsonDeserializer.TRUSTED_PACKAGES, "com.ecommerce.common.event"), false);
    }

    @AfterEach
    void close() {
        serializer.close();
        deserializer.close();
    }

    @Test
    void shouldRoundTripAnEvent() {
        OrderPaidEvent sent = OrderPaidEvent.builder()
                .orderId(42L)
                .orderNumber("CMD-2026-000042")
                .userId(7L)
                .totalInclTax(new BigDecimal("45.90"))
                .build();

        Object received = roundTrip(sent);

        assertThat(received).isEqualTo(sent);
    }

    @Test
    void shouldWriteTheEventDateAsIsoString() {
        OrderPaidEvent event = OrderPaidEvent.builder()
                .occurredAt(Instant.parse("2026-11-03T08:00:00Z"))
                .orderId(1L).orderNumber("CMD-1")
                .build();

        String json = new String(serializer.serialize(TOPIC, new RecordHeaders(), event));

        assertThat(json).contains("\"occurredAt\":\"2026-11-03T08:00:00Z\"");
        assertThat(roundTrip(event)).isEqualTo(event);
    }

    @Test
    void shouldRestoreTheConcreteTypeBehindASharedInterface() {
        PriceDroppedEvent drop = PriceDroppedEvent.builder()
                .productId(1L).variantId(10L)
                .previousUnitPriceExclTax(new BigDecimal("20.00"))
                .newUnitPriceExclTax(new BigDecimal("15.00"))
                .build();
        BackInStockEvent restock = BackInStockEvent.builder().productId(1L).variantId(11L).availableQuantity(5).build();

        assertThat(roundTrip(drop)).isInstanceOf(CatalogEvent.class).isEqualTo(drop);
        assertThat(roundTrip(restock)).isInstanceOf(CatalogEvent.class).isEqualTo(restock);
    }

    @Test
    void shouldIgnoreFieldsAddedByANewerProducer() {
        RecordHeaders headers = new RecordHeaders();
        byte[] body = serializer.serialize(TOPIC, headers,
                OrderPaidEvent.builder().orderId(1L).orderNumber("CMD-1").build());
        String withNewField = new String(body).replaceFirst("\\{", "{\"addedLater\":\"x\",");

        Object received = deserializer.deserialize(TOPIC, headers, withNewField.getBytes());

        assertThat(received).isInstanceOf(OrderPaidEvent.class);
    }

    @Test
    void shouldRefuseAClassOutsideTheSharedEvents() {
        RecordHeaders headers = new RecordHeaders();
        byte[] body = serializer.serialize(TOPIC, headers, new NotAnEvent("x"));

        assertThatThrownBy(() -> deserializer.deserialize(TOPIC, headers, body))
                .hasMessageContaining("not in the trusted packages");
    }

    @Test
    void shouldFillTheEnvelopeWhenTheProducerDoesNot() {
        OrderPaidEvent event = OrderPaidEvent.builder().orderId(1L).orderNumber("CMD-1").build();

        assertThat(event.eventId()).isNotNull();
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void shouldRejectAReplyEventWithoutTheUserToNotify() {
        assertThatThrownBy(() -> SupportAdminReplyEvent.builder().ticketId(1L).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessage("userId");
    }

    @Test
    void shouldRejectAPriceDropWithoutItsPrices() {
        assertThatThrownBy(() -> PriceDroppedEvent.builder().variantId(10L).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessage("previousUnitPriceExclTax");
    }

    @Test
    void shouldRejectAnEventWithoutItsIdentifier() {
        assertThatThrownBy(() -> OrderPaidEvent.builder().orderNumber("CMD-1").build())
                .isInstanceOf(NullPointerException.class)
                .hasMessage("orderId");
    }

    private Object roundTrip(Object event) {
        RecordHeaders headers = new RecordHeaders();
        byte[] body = serializer.serialize(TOPIC, headers, event);
        return deserializer.deserialize(TOPIC, headers, body);
    }

    record NotAnEvent(String value) {
    }
}
