package com.ecommerce.mcp.token;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Back-office management of MCP tokens (the panel of M04 #95). The gateway
 * reserves this path to the {@code admin} role.
 */
@RestController
@RequestMapping("/api/admin/mcp/tokens")
@RequiredArgsConstructor
public class AdminMcpTokenController {

    /** Set by the gateway from the validated token; a client cannot forge it. */
    static final String SUBJECT_HEADER = "X-Auth-Subject";

    private final McpTokenService tokenService;

    @GetMapping
    public List<McpTokenResponse> list() {
        return tokenService.list();
    }

    @GetMapping("/{id}")
    public McpTokenResponse get(@PathVariable Long id) {
        return tokenService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedMcpTokenResponse create(@Valid @RequestBody CreateMcpTokenRequest request,
                                          @RequestHeader(value = SUBJECT_HEADER, required = false) String actorId) {
        return tokenService.create(request, actorId);
    }

    @PostMapping("/{id}/revoke")
    public McpTokenResponse revoke(@PathVariable Long id,
                                   @RequestHeader(value = SUBJECT_HEADER, required = false) String actorId) {
        return tokenService.revoke(id, actorId);
    }
}
