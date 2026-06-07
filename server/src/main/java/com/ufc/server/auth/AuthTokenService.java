package com.ufc.server.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues and redeems single-use {@link AccountToken}s.
 *
 * <p>A token is 256 bits of {@link SecureRandom}, returned to the caller in raw
 * (URL-safe base64) form but persisted only as its SHA-256 hash. Redeeming is
 * single-use and time-limited; lookups are by hash so a stolen database row is
 * worthless.
 */
@Service
public class AuthTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountTokenRepository accountTokenRepository;

    public AuthTokenService(AccountTokenRepository accountTokenRepository) {
        this.accountTokenRepository = accountTokenRepository;
    }

    /** Mint a token for the user, invalidating any earlier one of the same type. Returns the RAW token. */
    @Transactional
    public String issue(Long userId, TokenType type, Duration ttl) {
        accountTokenRepository.deleteByUserIdAndType(userId, type);

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        AccountToken token = new AccountToken();
        token.setTokenHash(sha256Hex(raw));
        token.setUserId(userId);
        token.setType(type);
        token.setExpiresAt(Instant.now().plus(ttl));
        accountTokenRepository.save(token);
        return raw;
    }

    /**
     * Redeem a raw token, returning the owning user id. Throws {@link AuthException}
     * (400) if the token is unknown, the wrong type, already used, or expired —
     * deliberately the same error in every case so nothing is leaked.
     */
    @Transactional
    public Long consume(String rawToken, TokenType type) {
        AccountToken token = (rawToken == null || rawToken.isBlank())
            ? null
            : accountTokenRepository
                .findByTokenHashAndType(sha256Hex(rawToken), type)
                .orElse(null);

        if (
            token == null ||
            token.getUsedAt() != null ||
            token.getExpiresAt().isBefore(Instant.now())
        ) {
            throw new AuthException(
                HttpStatus.BAD_REQUEST,
                "invalid or expired token"
            );
        }
        token.setUsedAt(Instant.now());
        return token.getUserId();
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of()
                .formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
