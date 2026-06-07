package com.ufc.server.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountTokenRepository
    extends JpaRepository<AccountToken, Long> {
    Optional<AccountToken> findByTokenHashAndType(
        String tokenHash,
        TokenType type
    );

    /** Invalidate a user's outstanding tokens of a type before issuing a fresh one. */
    void deleteByUserIdAndType(Long userId, TokenType type);
}
