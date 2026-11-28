package com.ecommerce.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecommerce.order.entity.DeliveryMode;

class ShippingServiceTest {

    private ShippingService shippingService;

    @BeforeEach
    void setUp() {
        shippingService = new ShippingService();
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when delivery mode is null")
    void shouldThrowExceptionWhenModeIsNull() {
        assertThatThrownBy(() -> shippingService.calculateShippingCost(null, new BigDecimal("30.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Delivery mode is required");
    }

    @Test
    @DisplayName("Click and collect should be free (0.00)")
    void shouldReturnZeroForClickAndCollect() {
        BigDecimal cost = shippingService.calculateShippingCost(DeliveryMode.CLICK_AND_COLLECT, new BigDecimal("10.00"));
        assertThat(cost).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Relay point should cost 3.50")
    void shouldReturn350ForRelayPoint() {
        BigDecimal cost = shippingService.calculateShippingCost(DeliveryMode.RELAY_POINT, new BigDecimal("20.00"));
        assertThat(cost).isEqualByComparingTo(new BigDecimal("3.50"));
    }

    @Test
    @DisplayName("Standard delivery should cost 4.90 when subtotal is under 50.00")
    void shouldReturn490ForStandardUnderThreshold() {
        BigDecimal cost = shippingService.calculateShippingCost(DeliveryMode.STANDARD, new BigDecimal("49.99"));
        assertThat(cost).isEqualByComparingTo(new BigDecimal("4.90"));
    }

    @Test
    @DisplayName("Standard delivery should be free when subtotal is 50.00 or above")
    void shouldReturnZeroForStandardAtOrAboveThreshold() {
        BigDecimal costExact = shippingService.calculateShippingCost(DeliveryMode.STANDARD, new BigDecimal("50.00"));
        BigDecimal costAbove = shippingService.calculateShippingCost(DeliveryMode.STANDARD, new BigDecimal("120.00"));

        assertThat(costExact).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(costAbove).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Express delivery should cost 9.90")
    void shouldReturn990ForExpress() {
        BigDecimal cost = shippingService.calculateShippingCost(DeliveryMode.EXPRESS, new BigDecimal("150.00"));
        assertThat(cost).isEqualByComparingTo(new BigDecimal("9.90"));
    }
}
