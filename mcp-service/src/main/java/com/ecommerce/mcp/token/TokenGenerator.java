package com.ecommerce.mcp.token;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Makes the tokens and their fingerprints.
 *
 * <p>A token is {@code mcp_} followed by 256 random bits: guessing one is out
 * of reach, so a fast unsalted SHA-256 is the right fingerprint. A slow hash
 * (bcrypt) protects low-entropy passwords; here it would only cost a few
 * hundred milliseconds on every MCP request, and forbid looking the token up
 * by its fingerprint.
 */
@Component
public class TokenGenerator {

    public static final String TOKEN_PREFIX = "mcp_";

    /** What the back-office shows: the marker and the first characters of the random part. */
    static final int DISPLAY_PREFIX_LENGTH = TOKEN_PREFIX.length() + 8;

    private static final int RANDOM_BYTES = 32;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final SecureRandom random = new SecureRandom();

    public String newToken() {
        byte[] bytes = new byte[RANDOM_BYTES];
        random.nextBytes(bytes);
        return TOKEN_PREFIX + ENCODER.encodeToString(bytes);
    }

    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java runtime", e);
        }
    }

    public static String displayPrefix(String token) {
        return token.substring(0, DISPLAY_PREFIX_LENGTH);
    }
}
