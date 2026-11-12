package com.ecommerce.mcp.auth;

import com.ecommerce.mcp.token.McpTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.validation.ValidationConfigurationCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.time.Clock;

@Configuration
public class McpSecurityConfig {

    /**
     * Registered on the MCP paths only: the back-office API next to it
     * ({@code /api/admin/mcp/**}) is protected by the gateway and Keycloak.
     */
    @Bean
    FilterRegistrationBean<McpAuthenticationFilter> mcpAuthenticationFilter(
            McpTokenService tokenService, ObjectMapper objectMapper, McpSecurityProperties properties) {
        FilterRegistrationBean<McpAuthenticationFilter> registration =
                new FilterRegistrationBean<>(new McpAuthenticationFilter(tokenService, objectMapper));
        registration.setUrlPatterns(properties.protectedPaths());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Bean validation reads the same clock as the service: {@code @Future} on
     * {@code expiresAt} would otherwise compare against the system clock, and
     * a token could pass validation yet be expired for the service, or the
     * other way around.
     */
    @Bean
    ValidationConfigurationCustomizer validationClock(Clock clock) {
        return configuration -> configuration.clockProvider(() -> clock);
    }
}
