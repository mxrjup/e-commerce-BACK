package com.ecommerce.cart.service;

import com.ecommerce.cart.dto.AddCartItemRequest;
import com.ecommerce.cart.dto.CartResponse;
import com.ecommerce.cart.entity.Cart;
import com.ecommerce.cart.entity.CartItem;
import com.ecommerce.cart.repository.CartRepository;
import com.ecommerce.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class CartService {

    private final CartRepository cartRepository;

    public Cart getOrCreateCart(Long userId, String sessionToken) {
        
        if (userId != null) {
            return cartRepository.findByUserIdWithItems(userId)
                    .orElseGet(() -> cartRepository.save(new Cart(userId, null)));
        }

        if (sessionToken != null && !sessionToken.trim().isEmpty()) {
            return cartRepository.findBySessionTokenWithItems(sessionToken.trim())
                    .orElseGet(() -> cartRepository.save(new Cart(null, sessionToken.trim())));
        }

        String newSessionToken = UUID.randomUUID().toString();
        return cartRepository.save(new Cart(null, newSessionToken));
    }

    @Transactional(readOnly = true)
    public CartResponse getCart(Long userId, String sessionToken) {
        Cart cart = getOrCreateCart(userId, sessionToken);
        return CartResponse.fromEntity(cart);
    }

    public CartResponse addItem(Long userId, String sessionToken, AddCartItemRequest request) {
        Cart cart = getOrCreateCart(userId, sessionToken);

        Optional<CartItem> existingItem = cart.getItems().stream()
                .filter(item -> item.getVariantId().equals(request.getVariantId()))
                .findFirst();

        if (existingItem.isPresent()) {
            CartItem item = existingItem.get();
            item.setQuantity(item.getQuantity() + request.getQuantity());
        } else {
            CartItem newItem = new CartItem(cart, request.getVariantId(), request.getQuantity());
            cart.addItem(newItem);
        }

        Cart savedCart = cartRepository.save(cart);
        return CartResponse.fromEntity(savedCart);
    }


    public CartResponse updateItemQuantity(Long userId, String sessionToken, Long itemId, int newQuantity) {
        Cart cart = getOrCreateCart(userId, sessionToken);

        CartItem item = cart.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found, id: " + itemId));

        if (newQuantity <= 0) {
            cart.removeItem(item);
        } else {
            item.setQuantity(newQuantity);
        }

        Cart savedCart = cartRepository.save(cart);
        return CartResponse.fromEntity(savedCart);
    }


    public CartResponse removeItem(Long userId, String sessionToken, Long itemId) {
        Cart cart = getOrCreateCart(userId, sessionToken);

        CartItem item = cart.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found, id: " + itemId));

        cart.removeItem(item);

        Cart savedCart = cartRepository.save(cart);
        return CartResponse.fromEntity(savedCart);
    }


    public CartResponse clearCart(Long userId, String sessionToken) {
        Cart cart = getOrCreateCart(userId, sessionToken);

        cart.clearItems();

        Cart savedCart = cartRepository.save(cart);
        return CartResponse.fromEntity(savedCart);
    }


    public CartResponse mergeCart(Long userId, String sessionToken){

         if (userId == null) {
            throw new IllegalArgumentException("User ID must not be null for cart merge");
        }
        
        if(sessionToken == null || sessionToken.trim().isEmpty()){
            return getCart(userId, null);
        }

          Optional<Cart> guestCartOpt = cartRepository.findBySessionTokenWithItems(sessionToken.trim());

          if(guestCartOpt.isEmpty()){
            return getCart(userId, null);
          }

          Cart guestCart = guestCartOpt.get();
          Cart userCart = getOrCreateCart(userId, null);


          for(CartItem guestItem : guestCart.getItems()){

            Optional<CartItem> existingUserItem = userCart.getItems().stream().filter(
                ui -> ui.getVariantId().equals(guestItem.getVariantId())
            ).findFirst();

            if(existingUserItem.isPresent()){

                CartItem userItem = existingUserItem.get();
                userItem.setQuantity(userItem.getQuantity()+ guestItem.getQuantity());
            }else{
                userCart.addItem(new CartItem(userCart, guestItem.getVariantId(), guestItem.getQuantity()));
            }

        

          }

        cartRepository.delete(guestCart);
        Cart savedCart = cartRepository.save(userCart);

        return CartResponse.fromEntity(savedCart);

    }
}
