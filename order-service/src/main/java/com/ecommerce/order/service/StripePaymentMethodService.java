package com.ecommerce.order.service;

import com.ecommerce.order.dto.SavedPaymentMethodResponse;
import com.ecommerce.order.entity.CustomerProfile;
import com.ecommerce.order.entity.SavedPaymentMethod;
import com.ecommerce.order.repository.CustomerProfileRepository;
import com.ecommerce.order.repository.SavedPaymentMethodRepository;
import com.stripe.StripeClient;
import com.stripe.model.PaymentMethod;
import com.stripe.param.PaymentMethodAttachParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripePaymentMethodService {

    private final CustomerProfileRepository customerProfileRepository;
    private final SavedPaymentMethodRepository savedPaymentMethodRepository;
    private final StripeClient stripeClient;

    public String savePaymentMethod(Long userId, String paymentMethodId) {
        return savePaymentMethod(userId, paymentMethodId, false);
    }

    public String savePaymentMethod(Long userId, String paymentMethodId, boolean setDefault) {
        Optional<SavedPaymentMethod> existing = savedPaymentMethodRepository.findByUserIdAndStripePaymentMethodId(userId, paymentMethodId);
        if (existing.isPresent()) {
            SavedPaymentMethod savedMethod = existing.get();
            if (setDefault) {
                setDefaultPaymentMethod(userId, savedMethod.getStripePaymentMethodId());
            }
            log.info("Reusing saved Stripe payment method {} for user {}", paymentMethodId, userId);
            return savedMethod.getStripePaymentMethodId();
        }

        CustomerProfile profile = customerProfileRepository.findByUserId(userId)
            .orElseThrow(() -> new IllegalStateException("No Stripe customer profile found for user " + userId));

        if (profile.getStripeCustomerId() == null || profile.getStripeCustomerId().isBlank()) {
            throw new IllegalStateException("User " + userId + " has no Stripe customer attached");
        }

        try {
            PaymentMethod paymentMethod = stripeClient.paymentMethods().attach(
                paymentMethodId,
                PaymentMethodAttachParams.builder()
                    .setCustomer(profile.getStripeCustomerId())
                    .build()
            );

            SavedPaymentMethod savedPaymentMethod = new SavedPaymentMethod();
            savedPaymentMethod.setUserId(userId);
            savedPaymentMethod.setStripeCustomerId(profile.getStripeCustomerId());
            savedPaymentMethod.setStripePaymentMethodId(paymentMethod.getId());
            savedPaymentMethod.setBrand(paymentMethod.getCard() != null ? paymentMethod.getCard().getBrand() : null);
            savedPaymentMethod.setLast4(paymentMethod.getCard() != null ? paymentMethod.getCard().getLast4() : null);
            savedPaymentMethod.setDefaultPaymentMethod(setDefault);
            savedPaymentMethodRepository.save(savedPaymentMethod);

            if (setDefault) {
                setDefaultPaymentMethod(userId, paymentMethod.getId());
            }

            log.info("Attached Stripe payment method {} to customer {} for user {}", paymentMethod.getId(), profile.getStripeCustomerId(), userId);
            return paymentMethod.getId();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to attach Stripe payment method " + paymentMethodId + " to user " + userId, e);
        }
    }

    public List<SavedPaymentMethodResponse> getSavedPaymentMethods(Long userId) {
        return savedPaymentMethodRepository.findByUserId(userId).stream()
            .sorted(Comparator.comparing(SavedPaymentMethod::isDefaultPaymentMethod).reversed()
                .thenComparing(SavedPaymentMethod::getId))
            .map(this::toResponse)
            .toList();
    }

    public void setDefaultPaymentMethod(Long userId, String paymentMethodId) {
        List<SavedPaymentMethod> userMethods = savedPaymentMethodRepository.findByUserId(userId);
        SavedPaymentMethod selected = userMethods.stream()
            .filter(method -> method.getStripePaymentMethodId().equals(paymentMethodId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Payment method " + paymentMethodId + " not found for user " + userId));

        userMethods.forEach(method -> method.setDefaultPaymentMethod(false));
        selected.setDefaultPaymentMethod(true);
        savedPaymentMethodRepository.saveAll(userMethods);
    }

    private SavedPaymentMethodResponse toResponse(SavedPaymentMethod entity) {
        SavedPaymentMethodResponse response = new SavedPaymentMethodResponse();
        response.setId(entity.getId());
        response.setStripePaymentMethodId(entity.getStripePaymentMethodId());
        response.setBrand(entity.getBrand());
        response.setLast4(entity.getLast4());
        response.setDefaultPaymentMethod(entity.isDefaultPaymentMethod());
        return response;
    }
}
