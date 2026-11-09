package com.ecommerce.order.service;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import com.ecommerce.order.entity.DeliveryMode;
 
 
 
 
@Service
public class ShippingService {
    private static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("50.00");

  public BigDecimal calculateShippingCost(DeliveryMode mode, BigDecimal subTotalInclTax){

    if(mode == null){
        throw new IllegalArgumentException("Delivery mode is required");
    }

    switch (mode) {
        case CLICK_AND_COLLECT:
            return BigDecimal.ZERO;
        case RELAY_POINT:
            return new BigDecimal("3.50");
        case STANDARD:
            if (subTotalInclTax != null && subTotalInclTax.compareTo(FREE_SHIPPING_THRESHOLD) >= 0) {
                return BigDecimal.ZERO;
            }
            return new BigDecimal("4.90");
        case EXPRESS:
            return new BigDecimal("9.90");
        default:
            throw new IllegalArgumentException("Delivery mode unsupported: " + mode);
    }
  }
}
