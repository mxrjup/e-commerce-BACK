package com.ecommerce.order.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ecommerce.order.dto.CheckoutRequest;
import com.ecommerce.order.dto.CheckoutResponse;
import com.ecommerce.order.dto.OrderDetailResponse;
import com.ecommerce.order.dto.OrderHistoryResponse;
import com.ecommerce.order.service.OrderService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_SESSION_TOKEN = "X-Session-Token";

    private final OrderService orderService;

    @PostMapping("/checkout")
    public ResponseEntity<CheckoutResponse> checkout(
            @RequestHeader(value = HEADER_USER_ID, required = true) Long userId,
            @RequestHeader(value = HEADER_SESSION_TOKEN, required = false) String sessionToken,
            @Valid @RequestBody CheckoutRequest request) {

        CheckoutResponse response = orderService.checkout(userId, sessionToken, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/history")
    public ResponseEntity<List<OrderHistoryResponse>> getOrderHistory(
            @RequestHeader(value = HEADER_USER_ID, required = true) Long userId) {
        return ResponseEntity.ok(orderService.getOrderHistory(userId));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDetailResponse> getOrderDetail(
            @RequestHeader(value = HEADER_USER_ID, required = true) Long userId,
            @PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.getOrderDetail(userId, orderId));
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> handleStripeWebhook(
        @RequestHeader(value= "Stripe-Signature", required=false) String stripeSignature,
        @RequestBody String payload){
            

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
