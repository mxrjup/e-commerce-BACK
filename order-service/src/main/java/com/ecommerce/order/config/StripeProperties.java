package com.ecommerce.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

@Configuration
@ConfigurationProperties(prefix = "stripe")
@Getter
@Setter
public class StripeProperties {

    private String apiKey;
    private String successUrl;
    private String cancelUrl;
    private String webhookSecret;
}
