package com.ecommerce.order.controller;

import com.ecommerce.order.dto.CheckoutRequest;
import com.ecommerce.order.dto.CheckoutResponse;
import com.ecommerce.order.dto.OrderDetailResponse;
import com.ecommerce.order.dto.OrderHistoryResponse;
import com.ecommerce.order.dto.SavePaymentMethodRequest;
import com.ecommerce.order.dto.SavedPaymentMethodResponse;
import com.ecommerce.order.dto.SetDefaultPaymentMethodRequest;
import com.ecommerce.order.service.OrderService;
import com.ecommerce.order.service.StripePaymentMethodService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_SESSION_TOKEN = "X-Session-Token";

    private final OrderService orderService;
    private final StripePaymentMethodService stripePaymentMethodService;

    @PostMapping("/checkout")
    public ResponseEntity<CheckoutResponse> checkout(
            @RequestHeader(value = HEADER_USER_ID, required = true) Long userId,
            @RequestHeader(value = HEADER_SESSION_TOKEN, required = false) String sessionToken,
            @Valid @RequestBody CheckoutRequest request) {

        CheckoutResponse response = orderService.checkout(userId, sessionToken, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/payment-methods")
    public ResponseEntity<List<SavedPaymentMethodResponse>> getSavedPaymentMethods(
            @RequestHeader(value = HEADER_USER_ID, required = true) Long userId) {
        return ResponseEntity.ok(stripePaymentMethodService.getSavedPaymentMethods(userId));
    }

    @PostMapping("/payment-methods")
    public ResponseEntity<SavedPaymentMethodResponse> savePaymentMethod(
            @RequestHeader(value = HEADER_USER_ID, required = true) Long userId,
            @Valid @RequestBody SavePaymentMethodRequest request) {
        String paymentMethodId = stripePaymentMethodService.savePaymentMethod(userId, request.getPaymentMethodId());

        SavedPaymentMethodResponse response = new SavedPaymentMethodResponse();
        response.setStripePaymentMethodId(paymentMethodId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/payment-methods/default")
    public ResponseEntity<Void> setDefaultPaymentMethod(
            @RequestHeader(value = HEADER_USER_ID, required = true) Long userId,
            @Valid @RequestBody SetDefaultPaymentMethodRequest request) {
        stripePaymentMethodService.setDefaultPaymentMethod(userId, request.getPaymentMethodId());
        return ResponseEntity.ok().build();
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
            @RequestHeader(value = "Stripe-Signature", required = false) String stripeSignature,
            @RequestBody String payload) {

        if (stripeSignature == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        try {
            orderService.handleStripeWebhook(payload, stripeSignature);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
}
