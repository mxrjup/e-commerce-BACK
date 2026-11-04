package com.ecommerce.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

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
