package com.ecommerce.order.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SavedPaymentMethodResponse {
    private Long id;
    private String stripePaymentMethodId;
    private String brand;
    private String last4;
    private boolean defaultPaymentMethod;
}
