package com.ufc.server.auth;

import com.ufc.server.user.User;
import com.ufc.server.user.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Resolves the logged-in {@link User} from a request's {@code Authorization}
 * header. Shared by every controller that needs the current user so the
 * bearer-token logic lives in exactly one place.
 */
@Service
public class CurrentUserService {

    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;

    public CurrentUserService(
        SessionRepository sessionRepository,
        UserRepository userRepository
    ) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
    }

    /** Pulls the bearer token out of an {@code Authorization} header, or null. */
    public String extractToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        return authHeader.substring("Bearer ".length()).trim();
    }

    /** The user behind a valid, unexpired session, if any. */
    public Optional<User> resolveUser(String authHeader) {
        return resolveToken(extractToken(authHeader));
    }

    /** The user behind a raw session token (not a header). Used by the WebSocket handshake. */
    public Optional<User> resolveToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Optional<Session> session = sessionRepository.findById(token);
        if (
            session.isEmpty() ||
            session.get().getExpiresAt().isBefore(Instant.now())
        ) {
            return Optional.empty();
        }
        return userRepository.findById(session.get().getUserId());
    }

    /** Like {@link #resolveUser} but raises 401 instead of returning empty. */
    public User requireUser(String authHeader) {
        return resolveUser(authHeader).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthorized")
        );
    }
}
