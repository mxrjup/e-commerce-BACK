package com.ecommerce.order.service;
import com.ecommerce.order.entity.DeliveryMode;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderItem;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Year;
import java.util.Optional;
import org.mockito.Answers;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.ecommerce.order.dto.CheckoutItemRequest;
import com.ecommerce.order.dto.CheckoutRequest;
import com.ecommerce.order.dto.CheckoutResponse;
import com.stripe.StripeClient;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.kafka.core.KafkaTemplate;
import com.ecommerce.order.config.StripeProperties;
import com.ecommerce.order.event.OrderPaidEvent;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import static org.mockito.Mockito.*;




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
        Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_test_123456");
        when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/pay/cs_test_123456");
        when(stripeClient.checkout().sessions().create(any(SessionCreateParams.class))).thenReturn(mockSession);

        CheckoutItemRequest item1 = new CheckoutItemRequest(
                10L,
                "Souris gamer",
                new BigDecimal("50.00"),
                new BigDecimal("20.00"),
                1
        );
        CheckoutItemRequest item2 = new CheckoutItemRequest(
                20L,
                "Tapis de souris",
                new BigDecimal("20.00"),
                new BigDecimal("20.00"),
                1
        );

        CheckoutRequest request = new CheckoutRequest(
                100L,
                DeliveryMode.STANDARD,
                List.of(item1, item2)
        );

        CheckoutResponse response = orderService.checkout(1L, request);

        assertNotNull(response);
        assertNotNull(response.getOrderId());
        assertTrue(response.getOrderNumber().startsWith("CMD-"));
        assertEquals(new BigDecimal("84.00"), response.getTotalInclTax());
        assertEquals("cs_test_123456", response.getStripeSessionId());
        assertEquals("https://checkout.stripe.com/pay/cs_test_123456", response.getStripeCheckoutUrl());
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

        
        String payload = "{\"id\":\"evt_123\",\"object\":\"event\",\"api_version\":\"" + com.stripe.Stripe.API_VERSION + "\",\"type\":\"checkout.session.completed\",\"data\":{\"object\":{\"id\":\"cs_test_success_123\",\"object\":\"checkout.session\"}}}";
        String sigHeader = generateStripeSignature(payload, stripeProperties.getWebhookSecret());

      
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
    @DisplayName("Webhook with invalid signature throws IllegalArgumentException")
    void testHandleStripeWebhook_InvalidSignatureThrows() {
        String payload = "{\"id\":\"evt_125\",\"type\":\"checkout.session.completed\"}";
        String invalidSigHeader = "t=123456,v1=bad_signature";

       
        assertThrows(IllegalArgumentException.class, () ->
                orderService.handleStripeWebhook(payload, invalidSigHeader));
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



} 
