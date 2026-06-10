package com.ufc.server.auth;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.springframework.stereotype.Service;

/**
 * Mints opaque session tokens. Shared by {@link AuthService} (signup/login) and
 * agent provisioning ({@code AdminService.provisionAgent}) so the token logic
 * lives in exactly one place.
 */
@Service
public class SessionService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration SESSION_TTL = Duration.ofDays(7);

    private final SessionRepository sessionRepository;

    public SessionService(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    /**
     * Create and persist a fresh 7-day session for {@code userId}; returns the
     * random 256-bit opaque token (sent as {@code Authorization: Bearer <token>}).
     * Joins the caller's transaction if one is active.
     */
    public String createSession(Long userId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(bytes);
        Session session = new Session();
        session.setToken(token);
        session.setUserId(userId);
        session.setExpiresAt(Instant.now().plus(SESSION_TTL));
        sessionRepository.save(session);
        return token;
    }
}
