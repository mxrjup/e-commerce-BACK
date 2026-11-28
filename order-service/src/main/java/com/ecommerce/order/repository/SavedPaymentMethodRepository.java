package com.ecommerce.order.repository;

import com.ecommerce.order.entity.SavedPaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavedPaymentMethodRepository extends JpaRepository<SavedPaymentMethod, Long> {
    Optional<SavedPaymentMethod> findByUserIdAndStripePaymentMethodId(Long userId, String stripePaymentMethodId);
    List<SavedPaymentMethod> findByUserId(Long userId);
}
