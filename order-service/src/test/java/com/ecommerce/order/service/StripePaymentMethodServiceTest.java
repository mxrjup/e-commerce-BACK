package com.ecommerce.order.service;

import com.ecommerce.order.entity.CustomerProfile;
import com.ecommerce.order.entity.SavedPaymentMethod;
import com.ecommerce.order.repository.CustomerProfileRepository;
import com.ecommerce.order.repository.SavedPaymentMethodRepository;
import com.stripe.StripeClient;
import com.stripe.model.PaymentMethod;
import com.stripe.param.PaymentMethodAttachParams;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripePaymentMethodServiceTest {

    @Mock
    private CustomerProfileRepository customerProfileRepository;

    @Mock
    private SavedPaymentMethodRepository savedPaymentMethodRepository;

    @Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS)
    private StripeClient stripeClient;

    @InjectMocks
    private StripePaymentMethodService stripePaymentMethodService;

    @Test
    @DisplayName("reuse an already saved payment method for the customer")
    void savePaymentMethod_returnsExistingSavedMethod() {
        SavedPaymentMethod existing = new SavedPaymentMethod();
        existing.setUserId(12L);
        existing.setStripeCustomerId("cus_123");
        existing.setStripePaymentMethodId("pm_existing");
        when(savedPaymentMethodRepository.findByUserIdAndStripePaymentMethodId(12L, "pm_existing"))
            .thenReturn(Optional.of(existing));

        String paymentMethodId = stripePaymentMethodService.savePaymentMethod(12L, "pm_existing");

        assertEquals("pm_existing", paymentMethodId);
    }

    @Test
    @DisplayName("attach a new payment method to the Stripe customer and persist it")
    void savePaymentMethod_attachesNewPaymentMethodToCustomer() throws Exception {
        CustomerProfile profile = new CustomerProfile();
        profile.setUserId(17L);
        profile.setStripeCustomerId("cus_456");
        when(customerProfileRepository.findByUserId(17L)).thenReturn(Optional.of(profile));
        when(savedPaymentMethodRepository.findByUserIdAndStripePaymentMethodId(17L, "pm_new")).thenReturn(Optional.empty());

        PaymentMethod paymentMethod = new PaymentMethod();
        paymentMethod.setId("pm_new");
        paymentMethod.setType("card");
        PaymentMethod.Card card = new PaymentMethod.Card();
        card.setBrand("visa");
        card.setLast4("4242");
        paymentMethod.setCard(card);
        when(stripeClient.paymentMethods().attach(eq("pm_new"), any(PaymentMethodAttachParams.class))).thenReturn(paymentMethod);

        String savedPaymentMethodId = stripePaymentMethodService.savePaymentMethod(17L, "pm_new");

        assertEquals("pm_new", savedPaymentMethodId);

        ArgumentCaptor<SavedPaymentMethod> profileCaptor = ArgumentCaptor.forClass(SavedPaymentMethod.class);
        verify(savedPaymentMethodRepository).save(profileCaptor.capture());
        assertEquals("pm_new", profileCaptor.getValue().getStripePaymentMethodId());
        assertEquals("cus_456", profileCaptor.getValue().getStripeCustomerId());
        assertEquals("visa", profileCaptor.getValue().getBrand());
        assertEquals("4242", profileCaptor.getValue().getLast4());
    }
}
