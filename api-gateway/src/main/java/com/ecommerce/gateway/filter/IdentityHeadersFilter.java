package com.ecommerce.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Makes the identity a service reads trustworthy.
 *
 * <p>A client could send any {@code X-User-Id} and be served someone else's
 * data, so these headers are removed from every incoming request, whatever its
 * route, and set again from the validated token when there is one. A service
 * behind the gateway can therefore trust {@code X-Auth-Subject}: it can only
 * come from a signature Keycloak issued.
 *
 * <p>{@code X-Session-Token} is deliberately left alone: it identifies a guest
 * cart, not an account, and the client owns it.
 */
@Component
public class IdentityHeadersFilter implements GlobalFilter, Ordered {

    public static final String SUBJECT_HEADER = "X-Auth-Subject";

    private static final List<String> HEADERS_A_CLIENT_MAY_NOT_SET = List.of(SUBJECT_HEADER, "X-User-Id");
    private static final String NO_SUBJECT = "";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(JwtAuthenticationToken.class::isInstance)
                .map(authentication -> ((JwtAuthenticationToken) authentication).getToken().getSubject())
                .defaultIfEmpty(NO_SUBJECT)
                .flatMap(subject -> chain.filter(withTrustedIdentity(exchange, subject)));
    }

    private ServerWebExchange withTrustedIdentity(ServerWebExchange exchange, String subject) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    HEADERS_A_CLIENT_MAY_NOT_SET.forEach(headers::remove);
                    if (!NO_SUBJECT.equals(subject)) {
                        headers.set(SUBJECT_HEADER, subject);
                    }
                })
                .build();
        return exchange.mutate().request(request).build();
    }

    @Override
    public int getOrder() {
        // Avant le filtre de routage, qui recopie les en-tetes vers le service.
        return 0;
    }
}
