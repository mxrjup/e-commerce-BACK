package com.ecommerce.cart.controller;
import com.ecommerce.cart.dto.AddCartItemRequest;
import com.ecommerce.cart.dto.CartResponse;
import com.ecommerce.cart.dto.UpdateCartItemQuantityRequest;
import com.ecommerce.cart.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;




@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {
    private final CartService cartService;
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_SESSION_TOKEN = "X-Session-Token";



    @GetMapping
    public ResponseEntity<CartResponse> getCart(
            @RequestHeader(value = HEADER_USER_ID, required = false) Long userId,
            @RequestHeader(value = HEADER_SESSION_TOKEN, required = false) String sessionToken) {

        CartResponse response = cartService.getCart(userId, sessionToken);
        return buildResponseWithSessionHeader(response, HttpStatus.OK);
    }


    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItem(
            @RequestHeader(value = HEADER_USER_ID, required = false) Long userId,
            @RequestHeader(value = HEADER_SESSION_TOKEN, required = false) String sessionToken,
            @Valid @RequestBody AddCartItemRequest request) {

        CartResponse response = cartService.addItem(userId, sessionToken, request);
        return buildResponseWithSessionHeader(response, HttpStatus.CREATED);
    }

    @PatchMapping("/items/{itemId}")
    public ResponseEntity<CartResponse> updateItemQuantity(
            @RequestHeader(value = HEADER_USER_ID, required = false) Long userId,
            @RequestHeader(value = HEADER_SESSION_TOKEN, required = false) String sessionToken,
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateCartItemQuantityRequest request) {

        CartResponse response = cartService.updateItemQuantity(userId, sessionToken, itemId, request.getQuantity());
        return buildResponseWithSessionHeader(response, HttpStatus.OK);
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<CartResponse> removeItem(
            @RequestHeader(value = HEADER_USER_ID, required = false) Long userId,
            @RequestHeader(value = HEADER_SESSION_TOKEN, required = false) String sessionToken,
            @PathVariable Long itemId) {

        CartResponse response = cartService.removeItem(userId, sessionToken, itemId);
        return buildResponseWithSessionHeader(response, HttpStatus.OK);
    }

    @DeleteMapping
    public ResponseEntity<Void> clearCart(
         @RequestHeader(value = HEADER_USER_ID, required = false) Long userId,
         @RequestHeader(value = HEADER_SESSION_TOKEN, required = false) String sessionToken
    ){

        cartService.clearCart(userId, sessionToken);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<CartResponse> buildResponseWithSessionHeader(CartResponse cart, HttpStatus status) {
        HttpHeaders headers = new HttpHeaders();
        if (cart.getSessionToken() != null) {
            headers.add(HEADER_SESSION_TOKEN, cart.getSessionToken());
        }
        return new ResponseEntity<>(cart, headers, status);
    }

    @PostMapping("/merge")
    public ResponseEntity<CartResponse> mergeCart(
         @RequestHeader(value = HEADER_USER_ID, required = false) Long userId,
         @RequestHeader(value = HEADER_SESSION_TOKEN, required = false) String sessionToken
    ){

    CartResponse response = cartService.mergeCart(userId, sessionToken);

    return ResponseEntity.ok(response);

    }
}
