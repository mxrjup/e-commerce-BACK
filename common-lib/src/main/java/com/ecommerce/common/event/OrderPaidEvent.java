package com.ecommerce.common.event;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPaidEvent {

    private Long orderId;
    private String orderNumber;
    private Long userId;
    private String sessionToken;
    private BigDecimal totalInclTax;
}
