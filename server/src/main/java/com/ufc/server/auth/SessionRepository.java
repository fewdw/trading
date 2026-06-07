package com.ufc.server.auth;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRepository extends JpaRepository<Session, String> {
    /** Drop every session for a user — used to force re-login after a password reset. */
    void deleteByUserId(Long userId);
}
