package com.ecommerce.common.event;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderPaidEvent {

    private Long orderId;
    private String orderNumber;
    private Long userId;
    private String sessionToken;
    private BigDecimal totalInclTax;
}
