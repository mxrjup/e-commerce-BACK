package com.ecommerce.order.service;

import java.math.BigDecimal;
import java.time.Year;
import java.util.Optional;

import org.springframework.stereotype.Service;

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
            OrderItem item = new OrderItem(
                order,
                itemReq.getVariantId(),
                itemReq.getDesignation(),
                itemReq.getUnitPriceExclTax(),
                itemReq.getVatRate(),
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
                long unitPriceCents = item.getSubTotalInclTax()
                    .divide(BigDecimal.valueOf(item.getQuantity()), 2, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .longValue();

                params.addLineItem(
                    SessionCreateParams.LineItem.builder()
                        .setQuantity((long) item.getQuantity())
                        .setPriceData(
                            SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency("eur")
                                .setUnitAmount(unitPriceCents)
                                .setProductData(
                                    SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName(item.getDesignation())
                                        .build()
                                )
                                .build()
                        )
                        .build()
                );
            }

            if (shippingFee != null && shippingFee.compareTo(BigDecimal.ZERO) > 0) {
                long shippingCents = shippingFee.multiply(BigDecimal.valueOf(100)).longValue();
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
