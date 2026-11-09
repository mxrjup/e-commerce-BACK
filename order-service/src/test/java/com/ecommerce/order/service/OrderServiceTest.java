package com.ecommerce.order.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.ecommerce.common.event.OrderPaidEvent;
import com.ecommerce.common.event.OrderStatusChangedEvent;
import com.ecommerce.common.kafka.KafkaTopics;
import com.ecommerce.order.client.CatalogClient;
import com.ecommerce.order.client.CatalogVariantResponse;
import com.ecommerce.order.config.StripeProperties;
import com.ecommerce.order.dto.CheckoutItemRequest;
import com.ecommerce.order.dto.CheckoutRequest;
import com.ecommerce.order.dto.CheckoutResponse;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.entity.DeliveryMode;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderItem;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.repository.OrderRepository;
import com.stripe.StripeClient;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class OrderServiceTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
    private StripeClient stripeClient;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @MockBean
    private CatalogClient catalogClient;

    @Autowired
    private StripeProperties stripeProperties;

    @Test
    @DisplayName("Generating first number order (ex: CMD-2026-000001)")
    void testGenerateOrder_FirstOrder() {
        int currentYear = Year.now().getValue();
        String expected = "CMD-" + currentYear + "-000001";

        String orderNumber = orderService.generateOrderNumber();

        assertEquals(expected, orderNumber);
    }

    @Test
    @DisplayName("Incrementing number order")
    void testIncrementingOrderNumber() {
        int currentYear = Year.now().getValue();

        Order existingOrder = new Order();
        existingOrder.setUserId(1L);
        existingOrder.setOrderNumber("CMD-" + currentYear + "-000042");
        orderRepository.save(existingOrder);

        String nextOrderNumber = orderService.generateOrderNumber();

        assertEquals("CMD-" + currentYear + "-000043", nextOrderNumber);
    }

    @Test
    @DisplayName("Check persistance item of an order")
    void testOrderAndOrderItemPersistence_PriceHistory() {
        Order order = new Order();
        order.setUserId(1L);
        order.setOrderNumber(orderService.generateOrderNumber());
        order.setStatus(OrderStatus.NEW);
        order.setDeliveryMode(DeliveryMode.STANDARD);
        OrderItem item = new OrderItem(
            order,
            100L,
            "Écran 27 pouces 144Hz - Noir",
            new BigDecimal("150.00"),
            new BigDecimal("20.00"),
            2
        );
        order.addItem(item);

        Order savedOrder = orderRepository.save(order);

        assertNotNull(savedOrder.getId());
        Optional<Order> fetchedOrder = orderRepository.findByOrderNumber(order.getOrderNumber());
        assertTrue(fetchedOrder.isPresent());

        Order persisted = fetchedOrder.get();
        assertEquals(1, persisted.getItems().size());

        OrderItem persistedItem = persisted.getItems().get(0);
        assertEquals("Écran 27 pouces 144Hz - Noir", persistedItem.getDesignation());
        assertEquals(new BigDecimal("150.00"), persistedItem.getUnitPriceExclTax());
        assertEquals(new BigDecimal("20.00"), persistedItem.getVatRate());
        assertEquals(2, persistedItem.getQuantity());

        assertEquals(new BigDecimal("300.00"), persistedItem.getSubTotalExclTax());
        assertEquals(new BigDecimal("360.00"), persistedItem.getSubTotalInclTax());
    }

    @Test
    @DisplayName("Checkout calculates totals, shipping fee and returns Stripe session")
    void testCheckout_CalculatesTotalsAndCreatesStripeSession() throws Exception {
        when(catalogClient.getVariant(10L)).thenReturn(
            new CatalogVariantResponse(10L, "Souris gamer", new BigDecimal("50.00"), new BigDecimal("20.00"))
        );
        when(catalogClient.getVariant(20L)).thenReturn(
            new CatalogVariantResponse(20L, "Tapis de souris", new BigDecimal("20.00"), new BigDecimal("20.00"))
        );

        Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_test_123456");
        when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/pay/cs_test_123456");
        when(stripeClient.checkout().sessions().create(any(SessionCreateParams.class))).thenReturn(mockSession);

        CheckoutItemRequest item1 = new CheckoutItemRequest(10L, 1);
        CheckoutItemRequest item2 = new CheckoutItemRequest(20L, 1);
        CheckoutRequest request = new CheckoutRequest(
            100L,
            DeliveryMode.STANDARD,
            List.of(item1, item2)
        );

        CheckoutResponse response = orderService.checkout(1L, null, request);

        assertNotNull(response);
        assertNotNull(response.getOrderId());
        assertTrue(response.getOrderNumber().startsWith("CMD-"));
        assertEquals(new BigDecimal("84.00"), response.getTotalInclTax());
        assertEquals("cs_test_123456", response.getStripeSessionId());
        assertEquals("https://checkout.stripe.com/pay/cs_test_123456", response.getStripeCheckoutUrl());
    }

    @Test
    @DisplayName("Checkout sends exact line amount to Stripe avoiding any cent rounding drift")
    void testCheckout_ExactStripeAmountRounding() throws Exception {
        Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_test_exact");
        when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/pay/cs_test_exact");

        org.mockito.ArgumentCaptor<SessionCreateParams> paramsCaptor =
            org.mockito.ArgumentCaptor.forClass(SessionCreateParams.class);
        when(stripeClient.checkout().sessions().create(paramsCaptor.capture())).thenReturn(mockSession);

        when(catalogClient.getVariant(30L)).thenReturn(
            new CatalogVariantResponse(30L, "Livre technique", new BigDecimal("10.05"), new BigDecimal("5.50"))
        );

        CheckoutItemRequest item = new CheckoutItemRequest(30L, 3);
        CheckoutRequest request = new CheckoutRequest(100L, DeliveryMode.CLICK_AND_COLLECT, List.of(item));

        CheckoutResponse response = orderService.checkout(1L, null, request);

        assertEquals(new BigDecimal("31.81"), response.getTotalInclTax());

        SessionCreateParams params = paramsCaptor.getValue();
        long totalStripeCents = params.getLineItems().stream()
            .mapToLong(li -> li.getPriceData().getUnitAmount() * li.getQuantity())
            .sum();
        assertEquals(3181L, totalStripeCents);
        assertEquals("Livre technique (x3)", params.getLineItems().get(0).getPriceData().getProductData().getName());
        assertEquals(1L, params.getLineItems().get(0).getQuantity());
    }

    @Test
    @DisplayName("Admin can list filtered orders")
    void testGetOrdersForAdmin_FiltersByUserAndStatus() {
        Order order1 = new Order();
        order1.setUserId(9L);
        order1.setOrderNumber("CMD-2026-000200");
        order1.setStatus(OrderStatus.NEW);
        orderRepository.save(order1);

        Order order2 = new Order();
        order2.setUserId(9L);
        order2.setOrderNumber("CMD-2026-000201");
        order2.setStatus(OrderStatus.IN_PREPARATION);
        orderRepository.save(order2);

        List<OrderResponse> orders = orderService.getOrdersForAdmin(9L, OrderStatus.NEW, null, null);

        assertEquals(1, orders.size());
        assertEquals("CMD-2026-000200", orders.get(0).getOrderNumber());
    }

    @Test
    @DisplayName("Admin can fetch a complete order detail")
    void testGetOrderForAdmin_ReturnsOrderDetail() {
        Order order = new Order();
        order.setUserId(7L);
        order.setOrderNumber("CMD-2026-000310");
        order.setStatus(OrderStatus.SHIPPED);
        order.setShippingAddressId(123L);
        order.setDeliveryMode(DeliveryMode.STANDARD);
        order.setTotalExclTax(new BigDecimal("99.00"));
        order.setTotalInclTax(new BigDecimal("118.80"));

        OrderItem item = new OrderItem(order, 55L, "Casque audio", new BigDecimal("99.00"), new BigDecimal("20.00"), 1);
        order.addItem(item);

        Order saved = orderRepository.save(order);

        OrderResponse detail = orderService.getOrderForAdmin(saved.getId());

        assertEquals("CMD-2026-000310", detail.getOrderNumber());
        assertEquals(1, detail.getItems().size());
        assertEquals("Casque audio", detail.getItems().get(0).getDesignation());
    }

    @Test
    @DisplayName("Checkout restricts Stripe session to card payments")
    void testCheckout_StripeSessionUsesCardOnly() throws Exception {
        Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_test_card_only");
        when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/pay/cs_test_card_only");

        org.mockito.ArgumentCaptor<SessionCreateParams> paramsCaptor =
            org.mockito.ArgumentCaptor.forClass(SessionCreateParams.class);
        when(stripeClient.checkout().sessions().create(paramsCaptor.capture())).thenReturn(mockSession);

        when(catalogClient.getVariant(40L)).thenReturn(
            new CatalogVariantResponse(40L, "Chargeur USB-C", new BigDecimal("15.00"), new BigDecimal("20.00"))
        );

        CheckoutRequest request = new CheckoutRequest(100L, DeliveryMode.STANDARD, List.of(new CheckoutItemRequest(40L, 1)));
        orderService.checkout(1L, null, request);

        SessionCreateParams params = paramsCaptor.getValue();
        assertTrue(params.getPaymentMethodTypes().contains(SessionCreateParams.PaymentMethodType.CARD));
        assertEquals(List.of(SessionCreateParams.PaymentMethodType.CARD), params.getPaymentMethodTypes());
    }

    @Test
    @DisplayName("Checkout throws IllegalArgumentException when variant is not found in catalog")
    void testCheckout_VariantNotFound_ThrowsException() {
        when(catalogClient.getVariant(999L)).thenReturn(null);

        CheckoutItemRequest item = new CheckoutItemRequest(999L, 1);
        CheckoutRequest request = new CheckoutRequest(100L, DeliveryMode.STANDARD, List.of(item));

        assertThrows(IllegalArgumentException.class, () -> orderService.checkout(1L, null, request));
    }

    @Test
    @DisplayName("Webhook accepts async payment success events for deferred Stripe payments")
    void testHandleStripeWebhook_AsyncPaymentSucceeded() throws Exception {
        Order order = new Order();
        order.setUserId(1L);
        order.setOrderNumber("CMD-2026-000104");
        order.setStatus(OrderStatus.NEW);
        order.setStripeSessionId("cs_test_async_success");
        orderRepository.save(order);

        String payload = "{\"id\":\"evt_129\",\"object\":\"event\",\"api_version\":\"" + com.stripe.Stripe.API_VERSION + "\",\"type\":\"checkout.session.async_payment_succeeded\",\"data\":{\"object\":{\"id\":\"cs_test_async_success\",\"object\":\"checkout.session\",\"payment_status\":\"paid\"}}}";
        String sigHeader = generateStripeSignature(payload, stripeProperties.getWebhookSecret());
        when(kafkaTemplate.send(eq("order.paid"), eq("CMD-2026-000104"), any(OrderPaidEvent.class)))
            .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        orderService.handleStripeWebhook(payload, sigHeader);

        Order updated = orderRepository.findByStripeSessionId("cs_test_async_success").orElseThrow();
        assertEquals(OrderStatus.IN_PREPARATION, updated.getStatus());
        verify(kafkaTemplate).send(eq("order.paid"), eq("CMD-2026-000104"), any(OrderPaidEvent.class));
    }

    @Test
    @DisplayName("Webhook with valid signature marks order as IN_PREPARATION and publishes Kafka event")
    void testHandleStripeWebhook_Success() throws Exception {
        Order order = new Order();
        order.setUserId(1L);
        order.setOrderNumber("CMD-2026-000099");
        order.setStatus(OrderStatus.NEW);
        order.setStripeSessionId("cs_test_success_123");
        orderRepository.save(order);

        String payload = "{\"id\":\"evt_123\",\"object\":\"event\",\"api_version\":\"" + com.stripe.Stripe.API_VERSION + "\",\"type\":\"checkout.session.completed\",\"data\":{\"object\":{\"id\":\"cs_test_success_123\",\"object\":\"checkout.session\",\"payment_status\":\"paid\"}}}";
        String sigHeader = generateStripeSignature(payload, stripeProperties.getWebhookSecret());
        when(kafkaTemplate.send(eq("order.paid"), eq("CMD-2026-000099"), any(OrderPaidEvent.class)))
            .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        orderService.handleStripeWebhook(payload, sigHeader);

        Order updated = orderRepository.findByStripeSessionId("cs_test_success_123").orElseThrow();
        assertEquals(OrderStatus.IN_PREPARATION, updated.getStatus());
        verify(kafkaTemplate).send(eq("order.paid"), eq("CMD-2026-000099"), any(OrderPaidEvent.class));
    }

    @Test
    @DisplayName("Webhook is idempotent: does not republish Kafka event if order already IN_PREPARATION")
    void testHandleStripeWebhook_Idempotent() throws Exception {
        Order order = new Order();
        order.setUserId(1L);
        order.setOrderNumber("CMD-2026-000100");
        order.setStatus(OrderStatus.IN_PREPARATION);
        order.setStripeSessionId("cs_test_already_paid");
        orderRepository.save(order);

        String payload = "{\"id\":\"evt_124\",\"object\":\"event\",\"api_version\":\"" + com.stripe.Stripe.API_VERSION + "\",\"type\":\"checkout.session.completed\",\"data\":{\"object\":{\"id\":\"cs_test_already_paid\",\"object\":\"checkout.session\"}}}";
        String sigHeader = generateStripeSignature(payload, stripeProperties.getWebhookSecret());

        orderService.handleStripeWebhook(payload, sigHeader);

        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("Webhook ignores checkout.session.completed when payment_status is not paid")
    void testHandleStripeWebhook_IgnoresUnpaidSession() throws Exception {
        Order order = new Order();
        order.setUserId(1L);
        order.setOrderNumber("CMD-2026-000101");
        order.setStatus(OrderStatus.NEW);
        order.setStripeSessionId("cs_test_unpaid_123");
        orderRepository.save(order);

        String payload = "{\"id\":\"evt_125\",\"object\":\"event\",\"api_version\":\"2022-11-15\",\"type\":\"checkout.session.completed\",\"data\":{\"object\":{\"id\":\"cs_test_unpaid_123\",\"object\":\"checkout.session\",\"payment_status\":\"unpaid\"}}}";
        String sigHeader = generateStripeSignature(payload, stripeProperties.getWebhookSecret());

        orderService.handleStripeWebhook(payload, sigHeader);

        Order updated = orderRepository.findByStripeSessionId("cs_test_unpaid_123").orElseThrow();
        assertEquals(OrderStatus.NEW, updated.getStatus());
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("Webhook processes a legacy Stripe API payload when payment_status is paid")
    void testHandleStripeWebhook_LegacyApiVersionIsHandled() throws Exception {
        Order order = new Order();
        order.setUserId(1L);
        order.setOrderNumber("CMD-2026-000102");
        order.setStatus(OrderStatus.NEW);
        order.setStripeSessionId("cs_test_legacy_123");
        orderRepository.save(order);

        String payload = "{\"id\":\"evt_126\",\"object\":\"event\",\"api_version\":\"2022-11-15\",\"type\":\"checkout.session.completed\",\"data\":{\"object\":{\"id\":\"cs_test_legacy_123\",\"object\":\"checkout.session\",\"payment_status\":\"paid\"}}}";
        String sigHeader = generateStripeSignature(payload, stripeProperties.getWebhookSecret());
        when(kafkaTemplate.send(eq("order.paid"), eq("CMD-2026-000102"), any(OrderPaidEvent.class)))
            .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        orderService.handleStripeWebhook(payload, sigHeader);

        Order updated = orderRepository.findByStripeSessionId("cs_test_legacy_123").orElseThrow();
        assertEquals(OrderStatus.IN_PREPARATION, updated.getStatus());
        verify(kafkaTemplate).send(eq("order.paid"), eq("CMD-2026-000102"), any(OrderPaidEvent.class));
    }

    @Test
    @DisplayName("Webhook with invalid signature throws IllegalArgumentException")
    void testHandleStripeWebhook_InvalidSignatureThrows() {
        String payload = "{\"id\":\"evt_127\",\"type\":\"checkout.session.completed\"}";
        String invalidSigHeader = "t=123456,v1=bad_signature";

        assertThrows(IllegalArgumentException.class, () -> orderService.handleStripeWebhook(payload, invalidSigHeader));
    }

    @Test
    @DisplayName("Webhook with Kafka send failure throws IllegalStateException and keeps order NEW")
    void testHandleStripeWebhook_SendFailureRollsBack() throws Exception {
        Order order = new Order();
        order.setUserId(1L);
        order.setOrderNumber("CMD-2026-000103");
        order.setStatus(OrderStatus.NEW);
        order.setStripeSessionId("cs_test_kafka_failure");
        orderRepository.save(order);

        String payload = "{\"id\":\"evt_128\",\"object\":\"event\",\"api_version\":\"" + com.stripe.Stripe.API_VERSION + "\",\"type\":\"checkout.session.completed\",\"data\":{\"object\":{\"id\":\"cs_test_kafka_failure\",\"object\":\"checkout.session\",\"payment_status\":\"paid\"}}}";
        String sigHeader = generateStripeSignature(payload, stripeProperties.getWebhookSecret());
        when(kafkaTemplate.send(eq("order.paid"), eq("CMD-2026-000103"), any(OrderPaidEvent.class)))
            .thenThrow(new RuntimeException("Kafka down"));

        assertThrows(IllegalStateException.class, () -> orderService.handleStripeWebhook(payload, sigHeader));

        Order updated = orderRepository.findByStripeSessionId("cs_test_kafka_failure").orElseThrow();
        assertEquals(OrderStatus.NEW, updated.getStatus());
    }

    @Test
    @DisplayName("Status transition is allowed only for valid step changes")
    void testUpdateStatus_AllowsValidTransitions() {
        Order order = new Order();
        order.setOrderNumber("ORD-42");
        order.setStatus(OrderStatus.NEW);

        orderService.updateStatus(order, OrderStatus.IN_PREPARATION);
        assertEquals(OrderStatus.IN_PREPARATION, order.getStatus());

        orderService.updateStatus(order, OrderStatus.SHIPPED);
        assertEquals(OrderStatus.SHIPPED, order.getStatus());

        orderService.updateStatus(order, OrderStatus.DELIVERED);
        assertEquals(OrderStatus.DELIVERED, order.getStatus());
    }

    @Test
    @DisplayName("Status transition rejects invalid state changes")
    void testUpdateStatus_RejectsInvalidTransitions() {
        Order order = new Order();
        order.setStatus(OrderStatus.NEW);

        assertThrows(IllegalStateException.class, () -> orderService.updateStatus(order, OrderStatus.DELIVERED));
        assertThrows(IllegalStateException.class, () -> orderService.updateStatus(order, OrderStatus.CANCELLED));
    }

    private String generateStripeSignature(String payload, String secret) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        String payloadToSign = timestamp + "." + payload;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(payloadToSign.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) {
            String h = Integer.toHexString(0xff & b);
            if (h.length() == 1) hex.append('0');
            hex.append(h);
        }
        return "t=" + timestamp + ",v1=" + hex;
    }

    @Test
    void shouldPublishEventWhenStatusChanges() {
        Order order = new Order();
        order.setId(42L);
        order.setOrderNumber("ORD-42");
        order.setStatus(OrderStatus.NEW);

        orderService.updateStatus(order, OrderStatus.IN_PREPARATION);

        verify(kafkaTemplate).send(eq(KafkaTopics.ORDER_STATUS_CHANGED), any(OrderStatusChangedEvent.class));
    }

    @Test
    void shouldNotPublishEventWhenTransitionIsInvalid() {
        Order order = new Order();
        order.setStatus(OrderStatus.NEW);

        assertThrows(IllegalStateException.class,
            () -> orderService.updateStatus(order, OrderStatus.DELIVERED));

        verify(kafkaTemplate, never()).send(anyString(), any());
    }

}
