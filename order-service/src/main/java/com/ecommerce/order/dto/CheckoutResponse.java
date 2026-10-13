package com.ecommerce.order.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutResponse {
    private Long orderId;
    private String orderNumber;
    private BigDecimal totalInclTax;
    private String stripeSessionId;
    private String stripeCheckoutUrl;
}

