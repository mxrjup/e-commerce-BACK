package com.ecommerce.mcp.token;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface McpTokenRepository extends JpaRepository<McpToken, Long> {

    Optional<McpToken> findByHash(String hash);

    /**
     * Records a use, at most once per period: a client calling a tool several
     * times a second must not turn every call into a write.
     */
    @Modifying
    @Query("UPDATE McpToken t SET t.lastUsedAt = :now "
            + "WHERE t.id = :id AND (t.lastUsedAt IS NULL OR t.lastUsedAt < :notBefore)")
    int recordUse(@Param("id") Long id, @Param("now") Instant now, @Param("notBefore") Instant notBefore);
}
