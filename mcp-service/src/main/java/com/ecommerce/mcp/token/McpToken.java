package com.ecommerce.mcp.token;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "jeton_mcp")
public class McpToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nom", nullable = false, length = 100)
    private String name;

    @Column(name = "prefixe", nullable = false, length = 16, updatable = false)
    private String prefix;

    /** SHA-256 of the token, in hexadecimal. The token itself is never stored. */
    @Column(name = "empreinte", nullable = false, length = 64, updatable = false)
    private String hash;

    @Convert(converter = ScopesConverter.class)
    @Column(name = "portees", nullable = false, length = 500)
    private Set<McpScope> scopes;

    @Column(name = "cree_par", length = 64, updatable = false)
    private String createdBy;

    @Column(name = "revoque_par", length = 64)
    private String revokedBy;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "date_expiration")
    private Instant expiresAt;

    @Column(name = "date_revocation")
    private Instant revokedAt;

    @Column(name = "date_derniere_utilisation")
    private Instant lastUsedAt;

    public McpTokenStatus statusAt(Instant now) {
        if (revokedAt != null) {
            return McpTokenStatus.REVOKED;
        }
        if (expiresAt != null && !now.isBefore(expiresAt)) {
            return McpTokenStatus.EXPIRED;
        }
        return McpTokenStatus.ACTIVE;
    }
}
