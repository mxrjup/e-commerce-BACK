package com.ecommerce.order.service;

import java.math.BigDecimal;
import java.time.Year;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.ecommerce.order.client.CatalogClient;
import com.ecommerce.order.client.CatalogVariantResponse;
import com.ecommerce.order.config.StripeProperties;
import com.ecommerce.order.dto.CheckoutItemRequest;
import com.ecommerce.order.dto.CheckoutRequest;
import com.ecommerce.order.dto.CheckoutResponse;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderItem;
import com.ecommerce.order.repository.OrderRepository;
import com.stripe.StripeClient;
import com.stripe.model.checkout.Session;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import com.stripe.param.checkout.SessionCreateParams;
import java.math.RoundingMode;



@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ShippingService shippingService;
    private final StripeClient stripeClient;
    private final StripeProperties stripeProperties;
    private final CatalogClient catalogClient;



     /**
     * Generate an another order number : CMD-YYYY-XXXXXX.
     * Example : CMD-2026-000001
     */
    public String generateOrderNumber(){
        int year = Year.now().getValue();
        String prefix = "CMD-" + year + "-";

        Optional<Order> lastOrderOpt = orderRepository.findFirstByOrderNumberStartingWithOrderByOrderNumberDesc(prefix);

        int nextNumber = 1;
        if(!lastOrderOpt.isEmpty()){
            
            String lastOrderNumber = lastOrderOpt.get().getOrderNumber();
            String seqPart = lastOrderNumber.substring(lastOrderNumber.lastIndexOf('-') + 1);

            nextNumber = Integer.parseInt(seqPart)+1;
        }
        return prefix + String.format("%06d", nextNumber);
    }

    @Transactional
    public CheckoutResponse checkout(Long userId, CheckoutRequest request){
        Order order = new Order();
        order.setOrderNumber(generateOrderNumber());
        order.setUserId(userId);

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

            OrderItem item = new OrderItem(
                order,
                variant.id(),
                variant.designation(),
                variant.unitPriceExclTax(),
                variant.vatRate(),
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

}
