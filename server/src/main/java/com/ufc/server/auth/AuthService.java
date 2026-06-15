package com.ufc.server.auth;

import com.ufc.server.dto.LoginDTO;
import com.ufc.server.dto.SignupDTO;
import com.ufc.server.tasks.TreasurySeederTask;
import com.ufc.server.user.User;
import com.ufc.server.user.UserRepository;
import java.util.Optional;
import java.util.Set;
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

    /** Names that must never be claimed by a sign-up (impersonation/abuse). */
    private static final Set<String> RESERVED_USERNAMES = Set.of(
        "admin",
        "administrator",
        "root",
        "system",
        "treasury",
        "moderator",
        "support",
        "fightermarket"
    );

    /**
     * A small block-list of the most common/guessable passwords. Field-level
     * validation already enforces length; this stops the obvious weak choices a
     * length rule alone lets through. Mirrors the client's check.
     */
    private static final Set<String> WEAK_PASSWORDS = Set.of(
        "password",
        "password1",
        "password123",
        "12345678",
        "123456789",
        "1234567890",
        "qwertyui",
        "qwerty123",
        "11111111",
        "00000000",
        "iloveyou",
        "baseball",
        "football",
        "welcome1",
        "admin123",
        "letmein1",
        "abc12345",
        "fighter1"
    );

    /**
     * A valid bcrypt hash (cost 10) of a throwaway string — it matches no real
     * password. When a login names a user that doesn't exist (or the non-loginable
     * treasury account) we still run a bcrypt comparison against this, so a failed
     * login takes the same time whether or not the username exists. That closes the
     * timing side-channel an attacker could otherwise use to enumerate usernames.
     */
    private static final String DUMMY_HASH =
        "$2y$10$y75cuD2ScNOGt9vEwxmEceTUNqdiDMyMW.ZJvCijaok9sPWzq4NOu";

    private final UserRepository userRepository;
    private final SessionService sessionService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
        UserRepository userRepository,
        SessionService sessionService,
        PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.sessionService = sessionService;
        this.passwordEncoder = passwordEncoder;
    }

    public record LoginResult(String token, User user) {}

    @Transactional
    public LoginResult signup(SignupDTO dto) {
        String username = dto.username().trim();
        String password = dto.password();

        // Logical checks the field-level annotations can't express.
        if (password.equalsIgnoreCase(username)) {
            throw new AuthException(
                HttpStatus.BAD_REQUEST,
                "password cannot be the same as your username"
            );
        }
        if (isWeakPassword(password)) {
            throw new AuthException(
                HttpStatus.BAD_REQUEST,
                "password is too common — choose something harder to guess"
            );
        }
        if (
            RESERVED_USERNAMES.contains(username.toLowerCase()) ||
            username.equalsIgnoreCase(TreasurySeederTask.TREASURY_USERNAME)
        ) {
            throw new AuthException(
                HttpStatus.BAD_REQUEST,
                "that username isn't available"
            );
        }

        // Case-insensitive so "Bob" can't be registered alongside "bob".
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new AuthException(HttpStatus.CONFLICT, "username taken");
        }

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user = userRepository.save(user);
        log.info("New user signed up: {} (id={})", username, user.getId());

        return new LoginResult(sessionService.createSession(user.getId()), user);
    }

    @Transactional
    public LoginResult login(LoginDTO dto) {
        String username = dto.username().trim();
        Optional<User> maybeUser = userRepository.findByUsername(username);

        // The treasury is a system account and can never be logged into.
        boolean loginable =
            maybeUser.isPresent() &&
            !maybeUser
                .get()
                .getUsername()
                .equals(TreasurySeederTask.TREASURY_USERNAME);

        // Always run bcrypt — against the real hash when the user is loginable,
        // otherwise against DUMMY_HASH — so the response time never reveals
        // whether the username exists.
        String hash = loginable ? maybeUser.get().getPasswordHash() : DUMMY_HASH;
        boolean passwordMatches = passwordEncoder.matches(dto.password(), hash);

        if (!loginable || !passwordMatches) {
            throw new AuthException(
                HttpStatus.UNAUTHORIZED,
                "invalid credentials"
            );
        }

        User user = maybeUser.get();
        return new LoginResult(sessionService.createSession(user.getId()), user);
    }

    /** Rejects the most common passwords and trivially uniform ones (all one char). */
    private boolean isWeakPassword(String password) {
        return (
            WEAK_PASSWORDS.contains(password.toLowerCase()) ||
            password.chars().distinct().count() == 1
        );
    }
}
