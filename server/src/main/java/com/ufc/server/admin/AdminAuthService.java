package com.ufc.server.admin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Gatekeeper for the admin endpoints. The caller must present a shared secret in
 * the {@code X-Admin-Api-Key} header that matches {@code admin.api-key} (set via
 * the {@code ADMIN_API_KEY} environment variable).
 *
 * <p><b>Fail closed:</b> if no key is configured the admin endpoints are
 * disabled entirely, so a misconfiguration can never leave them wide open.
 */
@Service
public class AdminAuthService {

    private final String adminApiKey;

    public AdminAuthService(@Value("${admin.api-key:}") String adminApiKey) {
        this.adminApiKey = adminApiKey == null ? "" : adminApiKey.trim();
    }

    /** Throws unless {@code providedKey} matches the configured admin key. */
    public void requireAdmin(String providedKey) {
        if (adminApiKey.isBlank()) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "admin api key not configured"
            );
        }
        if (
            providedKey == null || !constantTimeEquals(providedKey, adminApiKey)
        ) {
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "invalid admin api key"
            );
        }
    }

    /** Compare without leaking length/contents through timing. */
    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
            a.getBytes(StandardCharsets.UTF_8),
            b.getBytes(StandardCharsets.UTF_8)
        );
    }
}
