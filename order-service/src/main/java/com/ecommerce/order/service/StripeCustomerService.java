package com.ecommerce.order.service;

import com.ecommerce.order.entity.CustomerProfile;
import com.ecommerce.order.repository.CustomerProfileRepository;
import com.stripe.StripeClient;
import com.stripe.model.Customer;
import com.stripe.param.CustomerCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripeCustomerService {

    private final CustomerProfileRepository customerProfileRepository;
    private final StripeClient stripeClient;

    public String getOrCreateStripeCustomer(Long userId, String email) {
        Optional<CustomerProfile> existing = customerProfileRepository.findByUserId(userId);
        if (existing.isPresent() && existing.get().getStripeCustomerId() != null) {
            log.info("Reusing Stripe customer {} for user {}", existing.get().getStripeCustomerId(), userId);
            return existing.get().getStripeCustomerId();
        }

        try {
            Customer customer = stripeClient.customers().create(
                CustomerCreateParams.builder()
                    .setEmail(email)
                    .build()
            );

            CustomerProfile profile = existing.orElseGet(CustomerProfile::new);
            profile.setUserId(userId);
            profile.setStripeCustomerId(customer.getId());
            profile.setStripeEmail(email);
            customerProfileRepository.save(profile);

            log.info("Created Stripe customer {} for user {}", customer.getId(), userId);
            return customer.getId();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create or retrieve Stripe customer for user " + userId, e);
        }
    }
}
