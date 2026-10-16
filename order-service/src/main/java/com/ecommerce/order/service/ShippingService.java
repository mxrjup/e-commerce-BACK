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

    return switch(mode){
        case CLICK_AND_COLLECT -> BigDecimal.ZERO;
        case RELAY_POINT -> new BigDecimal("3.50");
        case STANDARD -> {

            if(subTotalInclTax != null && subTotalInclTax.compareTo(FREE_SHIPPING_THRESHOLD) >= 0){
                yield BigDecimal.ZERO;
            }

            yield new BigDecimal("4.90");
        }
        case EXPRESS -> new BigDecimal("9.90");
    };
  }
}
