package com.ecommerce.mcp.token;

import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.mcp.auth.McpPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class McpTokenService {

    /** A use is recorded at most once per period, see {@link McpTokenRepository#recordUse}. */
    static final Duration LAST_USE_PRECISION = Duration.ofMinutes(1);

    private final McpTokenRepository repository;
    private final TokenGenerator generator;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<McpTokenResponse> list() {
        Instant now = clock.instant();
        return repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")))
                .stream().map(token -> McpTokenResponse.from(token, now)).toList();
    }

    @Transactional(readOnly = true)
    public McpTokenResponse get(Long id) {
        return McpTokenResponse.from(find(id), clock.instant());
    }

    @Transactional
    public CreatedMcpTokenResponse create(CreateMcpTokenRequest request, String actorId) {
        String value = generator.newToken();
        McpToken token = new McpToken();
        token.setName(request.name().trim());
        token.setPrefix(TokenGenerator.displayPrefix(value));
        token.setHash(TokenGenerator.hash(value));
        token.setScopes(EnumSet.copyOf(request.scopes()));
        token.setCreatedBy(actorId);
        token.setCreatedAt(clock.instant());
        token.setExpiresAt(request.expiresAt());
        McpToken saved = repository.saveAndFlush(token);
        return new CreatedMcpTokenResponse(McpTokenResponse.from(saved, clock.instant()), value);
    }

    /** Idempotent: revoking a revoked token keeps the first revocation. */
    @Transactional
    public McpTokenResponse revoke(Long id, String actorId) {
        McpToken token = find(id);
        if (token.getRevokedAt() == null) {
            token.setRevokedAt(clock.instant());
            token.setRevokedBy(actorId);
        }
        return McpTokenResponse.from(token, clock.instant());
    }

    /**
     * The principal of a token presented by an MCP client, if it is a token
     * this service issued and it is still active. Every failure looks the
     * same from outside: unknown, revoked or expired.
     */
    @Transactional
    public Optional<McpPrincipal> authenticate(String presented) {
        if (presented == null || !presented.startsWith(TokenGenerator.TOKEN_PREFIX)) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        return repository.findByHash(TokenGenerator.hash(presented))
                .filter(token -> token.statusAt(now) == McpTokenStatus.ACTIVE)
                .map(token -> {
                    repository.recordUse(token.getId(), now, now.minus(LAST_USE_PRECISION));
                    return new McpPrincipal(token.getId(), token.getName(), token.getScopes());
                });
    }

    private McpToken find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MCP token " + id + " not found"));
    }
}
