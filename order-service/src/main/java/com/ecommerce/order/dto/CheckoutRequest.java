package com.ecommerce.order.dto;

import java.util.List;

import com.ecommerce.order.entity.DeliveryMode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutRequest {

    @NotNull(message = "shippingAddressId is required")
    private Long shippingAddressId;

    @NotNull(message = "deliveryMode is required")
    private DeliveryMode deliveryMode;

    @NotEmpty(message = "items cannot be empty")
    @Valid
    private List<CheckoutItemRequest> items;

    
}
