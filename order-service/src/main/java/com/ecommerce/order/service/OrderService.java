package com.ecommerce.order.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.Year;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.ecommerce.common.event.OrderPaidEvent;
import com.ecommerce.common.event.OrderStatusChangedEvent;
import com.ecommerce.common.kafka.KafkaTopics;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.order.client.CatalogClient;
import com.ecommerce.order.client.CatalogVariantResponse;
import com.ecommerce.order.config.StripeProperties;
import com.ecommerce.order.dto.CheckoutItemRequest;
import com.ecommerce.order.dto.CheckoutRequest;
import com.ecommerce.order.dto.CheckoutResponse;
import com.ecommerce.order.dto.OrderDetailResponse;
import com.ecommerce.order.dto.OrderHistoryResponse;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderItem;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.repository.OrderRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.StripeClient;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final ShippingService shippingService;
    private final StripeClient stripeClient;
    private final StripeProperties stripeProperties;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final CatalogClient catalogClient;

    @Value("${app.kafka.topics.order-paid:order.paid}")
    private String orderPaidTopic;

    /**
     * Generate an another order number : CMD-YYYY-XXXXXX.
     * Example : CMD-2026-000001
     */
    public String generateOrderNumber() {
        int year = Year.now().getValue();
        String prefix = "CMD-" + year + "-";

        Optional<Order> lastOrderOpt = orderRepository.findFirstByOrderNumberStartingWithOrderByOrderNumberDesc(prefix);

        int nextNumber = 1;
        if (!lastOrderOpt.isEmpty()) {
            String lastOrderNumber = lastOrderOpt.get().getOrderNumber();
            String seqPart = lastOrderNumber.substring(lastOrderNumber.lastIndexOf('-') + 1);
            nextNumber = Integer.parseInt(seqPart) + 1;
        }
        return prefix + String.format("%06d", nextNumber);
    }

    public List<OrderResponse> getOrdersForAdmin(Long userId, OrderStatus status, Instant from, Instant to) {
        List<Order> orders;

        if (userId != null && status != null && from != null && to != null) {
            orders = orderRepository.findByUserIdAndStatusAndCreatedAtBetween(userId, status, from, to);
        } else if (userId != null && status != null) {
            orders = orderRepository.findByUserIdAndStatus(userId, status);
        } else if (userId != null && from != null && to != null) {
            orders = orderRepository.findByUserIdAndCreatedAtBetween(userId, from, to);
        } else if (status != null && from != null && to != null) {
            orders = orderRepository.findByStatusAndCreatedAtBetween(status, from, to);
        } else if (userId != null) {
            orders = orderRepository.findByUserId(userId);
        } else if (status != null) {
            orders = orderRepository.findByStatus(status);
        } else if (from != null && to != null) {
            orders = orderRepository.findByCreatedAtBetween(from, to);
        } else {
            orders = orderRepository.findAll();
        }

        return orders.stream()
            .sorted(Comparator.comparing(Order::getCreatedAt).reversed())
            .map(OrderResponse::fromEntity)
            .toList();
    }

    public OrderResponse getOrderForAdmin(Long orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Commande introuvable : " + orderId));
        return OrderResponse.fromEntity(order);
    }

    public List<OrderHistoryResponse> getOrderHistory(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
            .map(OrderHistoryResponse::fromEntity)
            .toList();
    }

    public OrderDetailResponse getOrderDetail(Long userId, Long orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Commande introuvable : " + orderId));

        if (!order.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Commande introuvable : " + orderId);
        }

        return OrderDetailResponse.fromEntity(order);
    }

    @Transactional
    public CheckoutResponse checkout(Long userId, String sessionToken, CheckoutRequest request) {
        Order order = new Order();
        order.setOrderNumber(generateOrderNumber());
        order.setUserId(userId);
        order.setSessionToken(sessionToken);
        order.setShippingAddressId(request.getShippingAddressId());
        order.setDeliveryMode(request.getDeliveryMode());
        order.setStatus(order.getStatus());

        BigDecimal totalHt = BigDecimal.ZERO;
        BigDecimal totalTtc = BigDecimal.ZERO;

        for (CheckoutItemRequest itemReq : request.getItems()) {
            CatalogVariantResponse variant = catalogClient.getVariant(itemReq.getVariantId());
            if (variant == null) {
                throw new IllegalArgumentException("Variante introuvable dans le catalogue : " + itemReq.getVariantId());
            }
            if (!variant.available()) {
                throw new IllegalArgumentException("Variante indisponible dans le catalogue : " + itemReq.getVariantId());
            }
            if (itemReq.getQuantity() > variant.stock()) {
                throw new IllegalArgumentException(
                    "Quantité demandée supérieure au stock disponible pour la variante " + itemReq.getVariantId() +
                        " (demande : " + itemReq.getQuantity() + ", stock : " + variant.stock() + ")"
                );
            }

            OrderItem item = new OrderItem(
                order,
                variant.id(),
                variant.designation(),
                variant.unitPriceExclTax(),
                variant.vatRate(),
                variant.purchasePriceExclTax(),
                itemReq.getQuantity()
            );
            order.addItem(item);

            totalHt = totalHt.add(item.getSubTotalExclTax());
            totalTtc = totalTtc.add(item.getSubTotalInclTax());
        }

        BigDecimal shippingFee = shippingService.calculateShippingCost(request.getDeliveryMode(), totalTtc);
        totalTtc = totalTtc.add(shippingFee);

        order.setTotalExclTax(totalHt);
        order.setTotalInclTax(totalTtc);
        orderRepository.save(order);

        Session session = createStripeSession(order, shippingFee);

        order.setStripeSessionId(session.getId());
        orderRepository.save(order);

        return CheckoutResponse.builder()
            .orderId(order.getId())
            .orderNumber(order.getOrderNumber())
            .totalInclTax(order.getTotalInclTax())
            .stripeSessionId(session.getId())
            .stripeCheckoutUrl(session.getUrl())
            .build();
    }

    private Session createStripeSession(Order order, BigDecimal shippingFee) {
        try {
            SessionCreateParams.Builder params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(stripeProperties.getSuccessUrl())
                .setCancelUrl(stripeProperties.getCancelUrl())
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                .putMetadata("orderNumber", order.getOrderNumber());

            for (OrderItem item : order.getItems()) {
                long subTotalCents = item.getSubTotalInclTax()
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();

                String lineName = item.getQuantity() > 1
                    ? item.getDesignation() + " (x" + item.getQuantity() + ")"
                    : item.getDesignation();

                params.addLineItem(
                    SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(
                            SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency("eur")
                                .setUnitAmount(subTotalCents)
                                .setProductData(
                                    SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName(lineName)
                                        .build()
                                )
                                .build()
                        )
                        .build()
                );
            }

            if (shippingFee != null && shippingFee.compareTo(BigDecimal.ZERO) > 0) {
                long shippingCents = shippingFee
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();
                params.addLineItem(
                    SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(
                            SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency("eur")
                                .setUnitAmount(shippingCents)
                                .setProductData(
                                    SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName("Frais de livraison")
                                        .build()
                                )
                                .build()
                        )
                        .build()
                );
            }

            return stripeClient.checkout().sessions().create(params.build());
        } catch (Exception e) {
            throw new RuntimeException("Erreur Stripe : " + e.getMessage(), e);
        }
    }

    @Transactional
    public void handleStripeWebhook(String payload, String sigHeader) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, stripeProperties.getWebhookSecret());
        } catch (SignatureVerificationException e) {
            log.error("Signature invalide pour le webhook Stripe : {}", e.getMessage());
            throw new IllegalArgumentException("Invalild Stripe signature : " + e.getMessage(), e);
        }

        log.info("Evènements Stripe reçu : {}", event.getType());

        if (!("checkout.session.completed".equals(event.getType())
            || "checkout.session.async_payment_succeeded".equals(event.getType()))) {
            return;
        }

        Session session;
        try {
            session = (Session) event.getDataObjectDeserializer().getObject()
                .orElseThrow(() -> new IllegalStateException("Impossible de désérialiser la session Stripe pour cette version d'API"));
        } catch (IllegalStateException e) {
            log.warn("La session Stripe ne peut pas être désérialisée avec la version SDK actuelle ; fallback sur le payload brut : {}", e.getMessage());
            session = getSessionFromRawPayload(payload);
        }

        if (session == null) {
            throw new IllegalStateException("Impossible de récupérer la session Stripe depuis le webhook");
        }

        String paymentStatus = session.getPaymentStatus();
        if (paymentStatus == null || !"paid".equalsIgnoreCase(paymentStatus)) {
            log.info("Événement Stripe ignoré pour la session {} : payment_status={} ", session.getId(), paymentStatus);
            return;
        }

        String sessionId = session.getId();
        log.info("Traitement du paiement validé pour la session Stripe : {}", sessionId);

        Order order = orderRepository.findByStripeSessionId(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Commande non trouvée pour la session Stripe : " + sessionId));

        if (order.getStatus() != OrderStatus.NEW) {
            log.info("Commande {} non éligible au traitement du webhook (statut actuel : {}). Évènement ignoré.",
                order.getOrderNumber(), order.getStatus());
            return;
        }

        OrderPaidEvent orderPaidEvent = OrderPaidEvent.builder()
            .orderId(order.getId())
            .orderNumber(order.getOrderNumber())
            .userId(order.getUserId())
            .sessionToken(order.getSessionToken())
            .totalInclTax(order.getTotalInclTax())
            .build();

        try {
            kafkaTemplate.send(orderPaidTopic, order.getOrderNumber(), orderPaidEvent)
                .get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Échec de publication Kafka pour la commande {} : {}", order.getOrderNumber(), e.getMessage(), e);
            throw new IllegalStateException("Impossible de publier l'évènement OrderPaid pour la commande " + order.getOrderNumber(), e);
        }

        String invoiceUrl = resolveInvoiceUrl(session);
        if (invoiceUrl != null) {
            order.setInvoiceUrl(invoiceUrl);
        }

        order.setStatus(OrderStatus.IN_PREPARATION);
        orderRepository.save(order);

        log.info("Evènements OrderPaid publié sur Kafka pour la commande {} ", order.getOrderNumber());
    }

    private String resolveInvoiceUrl(Session session) {
        if (session == null) {
            return null;
        }

        String invoiceId = session.getInvoice();
        if (invoiceId == null || invoiceId.isBlank()) {
            if (session.getInvoiceObject() != null && session.getInvoiceObject().getHostedInvoiceUrl() != null) {
                return session.getInvoiceObject().getHostedInvoiceUrl();
            }
            return null;
        }

        try {
            Invoice invoice = Invoice.retrieve(invoiceId);
            return invoice != null ? invoice.getHostedInvoiceUrl() : null;
        } catch (Exception e) {
            log.warn("Impossible de récupérer la facture Stripe {} pour la commande : {}", invoiceId, e.getMessage());
            return null;
        }
    }

    private Session getSessionFromRawPayload(String payload) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode root = objectMapper.readTree(payload);
            JsonNode dataNode = root.get("data");
            if (dataNode == null || !dataNode.isObject()) {
                return null;
            }

            JsonNode sessionNode = dataNode.get("object");
            if (sessionNode == null || !sessionNode.isObject()) {
                return null;
            }

            Session session = new Session();
            session.setId(sessionNode.has("id") && !sessionNode.get("id").isNull() ? sessionNode.get("id").asText() : null);
            session.setPaymentStatus(sessionNode.has("payment_status") && !sessionNode.get("payment_status").isNull()
                ? sessionNode.get("payment_status").asText()
                : null);
            session.setInvoice(sessionNode.has("invoice") && !sessionNode.get("invoice").isNull()
                ? sessionNode.get("invoice").asText()
                : null);
            return session;
        } catch (Exception e) {
            log.warn("Fallback Stripe payload JSON invalid : {}", e.getMessage());
            return null;
        }
    }

    public void updateStatus(Order order, OrderStatus nextStatus) {
        OrderStatus current = order.getStatus();

        boolean allowed = switch (current) {
            case NEW -> nextStatus == OrderStatus.IN_PREPARATION;
            case IN_PREPARATION -> nextStatus == OrderStatus.SHIPPED;
            case SHIPPED -> nextStatus == OrderStatus.DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };

        if (!allowed) {
            throw new IllegalStateException(
                "Transition invalide : " + current + " -> " + nextStatus
            );
        }

        order.setStatus(nextStatus);

        OrderStatusChangedEvent event = new OrderStatusChangedEvent(
            UUID.randomUUID(),
            Instant.now(),
            order.getId(),
            order.getOrderNumber(),
            current.name(),
            nextStatus.name()
        );

        kafkaTemplate.send(KafkaTopics.ORDER_STATUS_CHANGED, event);
    }
}
