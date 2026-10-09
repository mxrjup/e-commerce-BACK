package com.ecommerce.order.service;
import com.ecommerce.order.entity.DeliveryMode;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderItem;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.repository.OrderRepository;
import javax.crypto.extObjectInputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Year;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;




@SpringBootTest
@Transactional
class OrderServiceTest {

    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderRepository orderRepository;
  

    @Test
    @DisplayName("Generating first number order (ex: CMD-2026-000001)")
    void testGenerateOrder_FirstOrder() {

        int currentYear = Year.now().getValue();
        String expected = "CMD-" + currentYear + "-000001";

        String orderNumber = orderService.generateOrderNumber();

        assertEquals(expected, orderNumber);

    }

    @Test
    @DisplayName("Incrementing number order")
    void testIncrementingOrderNumber() {
        int currentYear = Year.now().getValue();

        Order existingOrder = new Order();   
        existingOrder.setUserId(1L);
        existingOrder.setOrderNumber("CMD" + currentYear + "-000042");
        orderRepository.save(existingOrder);
        
        String nextOrderNumber = orderService.generateOrderNumber();

        assertEquals("CMD" + currentYear + "-000043", nextOrderNumber);
    }

       @Test
    @DisplayName("Check persistance item of an order")
    void testOrderAndOrderItemPersistence_PriceHistory() {
        
        Order order = new Order();
        order.setUserId(1L);
        order.setOrderNumber(orderService.generateOrderNumber());
        order.setStatus(OrderStatus.NEW);
        order.setDeliveryMode(DeliveryMode.STANDARD);
        OrderItem item = new OrderItem(
                order,
                100L,
                "Écran 27 pouces 144Hz - Noir",
                new BigDecimal("150.00"), 
                new BigDecimal("20.00"),  
                2                        
        );
        order.addItem(item);
        
        Order savedOrder = orderRepository.save(order);
       
        assertNotNull(savedOrder.getId());
        Optional<Order> fetchedOrder = orderRepository.findByOrderNumber(order.getOrderNumber());
        assertTrue(fetchedOrder.isPresent());


        Order persisted = fetchedOrder.get();
        assertEquals(1, persisted.getItems().size());

        
        OrderItem persistedItem = persisted.getItems().get(0);
        assertEquals("Écran 27 pouces 144Hz - Noir", persistedItem.getDesignation());
        assertEquals(new BigDecimal("150.00"), persistedItem.getUnitPriceExclTax());
        assertEquals(new BigDecimal("20.00"), persistedItem.getVatRate());
        assertEquals(2, persistedItem.getQuantity());
       
        assertEquals(new BigDecimal("300.00"), persistedItem.getSubTotalExclTax());
        assertEquals(new BigDecimal("360.00"), persistedItem.getSubTotalInclTax());
    }

} 
