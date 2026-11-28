package com.ecommerce.users.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Configuration
public class InternalReadSecurityConfig {
    @Bean
    @Order(1)
    SecurityFilterChain internalReads(HttpSecurity http,
            @Value("${mcp.internal-read-key:}") String key) throws Exception {
        return http.securityMatcher("/api/internal/users", "/api/internal/users/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/internal/users", "/api/internal/users/**")
                        .access((authentication, context) -> {
                            String supplied = context.getRequest().getHeader("X-MCP-Service-Key");
                            return new AuthorizationDecision(!key.isBlank() && supplied != null
                                    && MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8),
                                    supplied.getBytes(StandardCharsets.UTF_8)));
                        })
                        .anyRequest().denyAll())
                .build();
    }
}
