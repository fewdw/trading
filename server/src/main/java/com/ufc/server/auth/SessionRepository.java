package com.ufc.server.auth;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRepository extends JpaRepository<Session, String> {
    /** Drop every session for a user — used to force re-login after a password reset. */
    void deleteByUserId(Long userId);

    /** Delete all sessions that expired before {@code cutoff}; returns the count removed. */
    long deleteByExpiresAtBefore(Instant cutoff);
}
