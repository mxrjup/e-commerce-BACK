package com.ecommerce.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.stripe.StripeClient;

@Configuration
public class StripeConfig {

    @Bean
    public StripeClient stripeClient(StripeProperties properties) {
        String apiKey = properties.getApiKey() != null ? properties.getApiKey() : "sk_test_placeholder";
        return new StripeClient(apiKey);
    }
}
