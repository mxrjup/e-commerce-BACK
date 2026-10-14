package com.ecommerce.order.controller;
import java.security.Signature;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ecommerce.order.dto.CheckoutRequest;
import com.ecommerce.order.dto.CheckoutResponse;
import com.ecommerce.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    public static final String HEADER_USER_ID = "X-User-Id";

    private final OrderService orderService;

    @PostMapping("/checkout")
    public ResponseEntity<CheckoutResponse> checkout(
            @RequestHeader(value = HEADER_USER_ID, required = true) Long userId,
            @Valid @RequestBody CheckoutRequest request) {

        CheckoutResponse response = orderService.checkout(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/webhook")
    public ResponseEntity<CheckoutResponse> handleStripeWebhook(
        @RequestHeader(value= "Stripe-Signature", required=false) String stripeSignature,
        @Valid @RequestBody  String payload){
            

            if(stripeSignature == null){
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            try{
                orderService.handleStripeWebhook(payload, stripeSignature);
                return ResponseEntity.ok().build();
            }catch( IllegalArgumentException e){
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
        }
}
