package com.ecommerce.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.stripe.StripeClient;

@Configuration
public class StripeConfig {

    @Bean
    public StripeClient stripeClient(StripeProperties properties) {
        return new StripeClient(properties.getApiKey());
    }
}
