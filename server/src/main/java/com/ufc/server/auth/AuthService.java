package com.ufc.server.auth;

import com.ufc.server.dto.LoginDTO;
import com.ufc.server.dto.SignupDTO;
import com.ufc.server.tasks.TreasurySeederTask;
import com.ufc.server.user.User;
import com.ufc.server.user.UserRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account lifecycle: signup and login.
 *
 * <p>Security choices: passwords are bcrypt-hashed and sessions are random
 * 256-bit opaque tokens. Authentication is username + password only — there is
 * no email on file, so no verification or password-reset flow. Signup logs the
 * new user in immediately.
 */
@Slf4j
@Service
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration SESSION_TTL = Duration.ofDays(7);

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
        UserRepository userRepository,
        SessionRepository sessionRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public record LoginResult(String token, User user) {}

    @Transactional
    public LoginResult signup(SignupDTO dto) {
        String username = dto.username().trim();

        // Logical checks the field-level annotations can't express.
        if (dto.password().equalsIgnoreCase(username)) {
            throw new AuthException(
                HttpStatus.BAD_REQUEST,
                "password cannot be the same as your username"
            );
        }

        if (userRepository.existsByUsername(username)) {
            throw new AuthException(HttpStatus.CONFLICT, "username taken");
        }

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(dto.password()));
        user = userRepository.save(user);
        log.info("New user signed up: {} (id={})", username, user.getId());

        return new LoginResult(createSession(user.getId()), user);
    }

    @Transactional
    public LoginResult login(LoginDTO dto) {
        String username = dto.username().trim();
        Optional<User> maybeUser = userRepository.findByUsername(username);
        boolean badCredentials =
            maybeUser.isEmpty() ||
            maybeUser
                .get()
                .getUsername()
                .equals(TreasurySeederTask.TREASURY_USERNAME) ||
            !passwordEncoder.matches(
                dto.password(),
                maybeUser.get().getPasswordHash()
            );
        if (badCredentials) {
            throw new AuthException(
                HttpStatus.UNAUTHORIZED,
                "invalid credentials"
            );
        }

        User user = maybeUser.get();
        return new LoginResult(createSession(user.getId()), user);
    }

    private String createSession(Long userId) {
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
