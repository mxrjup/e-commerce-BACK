package com.ecommerce.order.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SetDefaultPaymentMethodRequest {
    @NotBlank
    private String paymentMethodId;
}
