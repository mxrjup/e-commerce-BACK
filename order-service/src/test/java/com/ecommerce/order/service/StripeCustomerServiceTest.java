package com.ecommerce.order.service;

import com.ecommerce.order.entity.CustomerProfile;
import com.ecommerce.order.repository.CustomerProfileRepository;
import com.stripe.StripeClient;
import com.stripe.model.Customer;
import com.stripe.param.CustomerCreateParams;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeCustomerServiceTest {

    @Mock
    private CustomerProfileRepository customerProfileRepository;

    @Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS)
    private StripeClient stripeClient;

    @InjectMocks
    private StripeCustomerService stripeCustomerService;

    @Test
    @DisplayName("reuse an existing Stripe customer id when already stored")
    void getOrCreateStripeCustomer_returnsExistingCustomerId() throws Exception {
        CustomerProfile profile = new CustomerProfile();
        profile.setUserId(42L);
        profile.setStripeCustomerId("cus_existing_123");
        when(customerProfileRepository.findByUserId(42L)).thenReturn(Optional.of(profile));

        String customerId = stripeCustomerService.getOrCreateStripeCustomer(42L, "alice@example.com");

        assertEquals("cus_existing_123", customerId);
    }

    @Test
    @DisplayName("create a Stripe customer when no customer is stored yet")
    void getOrCreateStripeCustomer_createsAndPersistsCustomerWhenMissing() throws Exception {
        when(customerProfileRepository.findByUserId(99L)).thenReturn(Optional.empty());

        Customer customer = new Customer();
        customer.setId("cus_new_456");
        when(stripeClient.customers().create(any(CustomerCreateParams.class))).thenReturn(customer);

        String customerId = stripeCustomerService.getOrCreateStripeCustomer(99L, "bob@example.com");

        assertEquals("cus_new_456", customerId);

        ArgumentCaptor<CustomerProfile> profileCaptor = ArgumentCaptor.forClass(CustomerProfile.class);
        org.mockito.Mockito.verify(customerProfileRepository).save(profileCaptor.capture());
        assertEquals("cus_new_456", profileCaptor.getValue().getStripeCustomerId());
        assertEquals(99L, profileCaptor.getValue().getUserId());
    }
}
