package com.ecommerce.cart.service;

import com.ecommerce.cart.dto.AddCartItemRequest;
import com.ecommerce.cart.dto.CartResponse;
import com.ecommerce.cart.entity.Cart;
import com.ecommerce.cart.exception.ResourceNotFoundException;
import com.ecommerce.cart.repository.CartRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class CartServiceTest {

    @Autowired
    private CartService cartService;

    @Autowired
    private CartRepository cartRepository;

    @Test
    @DisplayName("Guest without token: creates and persists a new guest cart in database")
    void testGetOrCreateCart_GuestNew() {
        Cart cart = cartService.getOrCreateCart(null, null);

        assertNotNull(cart);
        assertNotNull(cart.getId());
        assertNotNull(cart.getSessionToken());
        assertNull(cart.getUserId());
        assertTrue(cartRepository.findById(cart.getId()).isPresent());
    }


    @Test
    @DisplayName("Add a new item to cart and persist in database")
    void testAddItem_New() {
        AddCartItemRequest request = new AddCartItemRequest(100L, 2);
        CartResponse response = cartService.addItem(null, "my-session-token", request);

        assertEquals(1, response.getItems().size());
        assertEquals(100L, response.getItems().get(0).getVariantId());
        assertEquals(2, response.getItems().get(0).getQuantity());
        assertEquals(2, response.getTotalItems());
    }

    @Test
    @DisplayName("Add existing variant increments quantity in database")
    void testAddItem_IncrementExisting() {
        cartService.addItem(null, "my-session-token", new AddCartItemRequest(100L, 2));
        CartResponse response = cartService.addItem(null, "my-session-token", new AddCartItemRequest(100L, 3));

        assertEquals(1, response.getItems().size());
        assertEquals(5, response.getItems().get(0).getQuantity());
        assertEquals(5, response.getTotalItems());
    }

    @Test
    @DisplayName("Updating item quantity to 0 removes the row from database")
    void testUpdateItemQuantity_ZeroRemovesItem() {
        CartResponse added = cartService.addItem(null, "my-session-token", new AddCartItemRequest(100L, 2));
        Long itemId = added.getItems().get(0).getId();

        CartResponse updated = cartService.updateItemQuantity(null, "my-session-token", itemId, 0);

        assertTrue(updated.getItems().isEmpty());
        assertEquals(0, updated.getTotalItems());
    }

    @Test
    @DisplayName("Updating non-existent item throws ResourceNotFoundException")
    void testUpdateItemQuantity_NotFound() {
        assertThrows(ResourceNotFoundException.class, () ->
                cartService.updateItemQuantity(null, "my-session-token", 99999L, 5));
    }

    @Test
    @DisplayName("Removing an item deletes it from cart")
    void testRemoveItem() {
        CartResponse added = cartService.addItem(null, "my-session-token", new AddCartItemRequest(100L, 2));
        Long itemId = added.getItems().get(0).getId();

        CartResponse removed = cartService.removeItem(null, "my-session-token", itemId);

        assertTrue(removed.getItems().isEmpty());
    }

    @Test
    @DisplayName("Clear cart empties all lines")
    void testClearCart() {
        cartService.addItem(null, "my-session-token", new AddCartItemRequest(100L, 2));
        cartService.addItem(null, "my-session-token", new AddCartItemRequest(101L, 1));

        CartResponse cleared = cartService.clearCart(null, "my-session-token");

        assertTrue(cleared.getItems().isEmpty());
        assertEquals(0, cleared.getTotalItems());
    }
}
