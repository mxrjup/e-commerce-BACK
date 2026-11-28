package com.ecommerce.mcp.auth;

import com.ecommerce.common.api.ErrorResponse;
import com.ecommerce.mcp.token.McpTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

/**
 * Lets an MCP request through only with an active token:
 * {@code Authorization: Bearer mcp_...}.
 *
 * <p>The gateway does not validate these tokens, they are not Keycloak JWTs:
 * this filter is the only check. A missing, unknown, revoked or expired token
 * gets the same 401, so a caller learns nothing about which tokens exist.
 */
public class McpAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";
    private static final String CHALLENGE = "Bearer realm=\"mcp\"";
    private static final String REJECTED = "A valid MCP token is required";

    private final McpTokenService tokenService;
    private final ObjectMapper objectMapper;

    public McpAuthenticationFilter(McpTokenService tokenService, ObjectMapper objectMapper) {
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<McpPrincipal> principal = bearerToken(request).flatMap(tokenService::authenticate);
        if (principal.isEmpty()) {
            reject(response);
            return;
        }
        request.setAttribute(McpAuthentication.ATTRIBUTE, principal.get());
        chain.doFilter(request, response);
    }

    private static Optional<String> bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            return Optional.empty();
        }
        return Optional.of(header.substring(BEARER.length()).trim()).filter(token -> !token.isEmpty());
    }

    private void reject(HttpServletResponse response) throws IOException {
        HttpStatus status = HttpStatus.UNAUTHORIZED;
        response.setStatus(status.value());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, CHALLENGE);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(REJECTED)
                .build();
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
