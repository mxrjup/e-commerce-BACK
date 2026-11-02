package com.ecommerce.order.service;

import java.time.Year;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.ecommerce.order.entity.Order;
import com.ecommerce.order.repository.OrderRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;


@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;


     /**
     * Generate an another order number : CMD-YYYY-XXXXXX.
     * Example : CMD-2026-000001
     */
    public String generateOrderNumber(){
        int year = Year.now().getValue();
        String prefix = "CMD-" + year + "-";

        Optional<Order> lastOrderOpt = orderRepository.findFirstByOrderNumberStartingWithOrderByOrderNumberDesc(prefix);

        int nextNumber = 1;
        if(!lastOrderOpt.isEmpty()){
            
            String lastOrderNumber = lastOrderOpt.get().getOrderNumber();
            String seqPart = lastOrderNumber.substring(lastOrderNumber.lastIndexOf('-') + 1);

            nextNumber = Integer.parseInt(seqPart)+1;

        }

        return prefix + String.format("%06d", nextNumber);
    }
}
