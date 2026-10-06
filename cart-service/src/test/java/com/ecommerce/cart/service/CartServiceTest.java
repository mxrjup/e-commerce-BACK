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

    @Test
    @DisplayName("Merge guest cart into user cart : sums duplicate items quantity and remove guest card")
    void testMergeCart(){

        ///Add items to logged in user cart
        cartService.addItem(1L, null, new AddCartItemRequest(100L, 2));

        ///Add items to guest cart
        cartService.addItem(null, "guest-token-1", new AddCartItemRequest(100L, 3));
        cartService.addItem(null, "guest-token-1", new AddCartItemRequest(200L, 1));

        CartResponse mergedCart = cartService.mergeCart(1L, "guest-token-1");

        //Check if we have 2 differents products in our cart (5 articles with id=1L + 1 article with id=200L -> 2 differents type of articles)
        assertEquals(2, mergedCart.getItems().size());

        //Check the number of product in our cart
        assertEquals(6, mergedCart.getTotalItems());

        //Check if the guest card is correctly removed after the merge
        assertTrue(cartRepository.findBySessionTokenWithItems("guest-token-1").isEmpty());
    }
}
