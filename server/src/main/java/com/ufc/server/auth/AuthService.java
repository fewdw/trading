package com.ufc.server.auth;

import com.ufc.server.dto.ForgotPasswordDTO;
import com.ufc.server.dto.LoginDTO;
import com.ufc.server.dto.ResendVerificationDTO;
import com.ufc.server.dto.ResetPasswordDTO;
import com.ufc.server.dto.SignupDTO;
import com.ufc.server.dto.VerifyEmailDTO;
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
 * Account lifecycle: signup, email verification, login, and password reset.
 *
 * <p>Security choices: passwords are bcrypt-hashed; verification/reset use
 * hashed single-use tokens (see {@link AuthTokenService}); login is blocked
 * until the email is verified; password reset invalidates all of the user's
 * sessions; and the "forgot password"/"resend" flows never reveal whether an
 * account exists.
 */
@Slf4j
@Service
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration SESSION_TTL = Duration.ofDays(7);
    private static final Duration VERIFICATION_TTL = Duration.ofHours(24);
    private static final Duration RESET_TTL = Duration.ofHours(1);

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenService authTokenService;
    private final EmailService emailService;

    public AuthService(
        UserRepository userRepository,
        SessionRepository sessionRepository,
        PasswordEncoder passwordEncoder,
        AuthTokenService authTokenService,
        EmailService emailService
    ) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.authTokenService = authTokenService;
        this.emailService = emailService;
    }

    public record LoginResult(String token, User user) {}

    @Transactional
    public void signup(SignupDTO dto) {
        String email = normalizeEmail(dto.email());
        String username = dto.username().trim();

        // Re-signing up with an email that already exists but is still
        // unverified just resends the confirmation — no duplicate, no error.
        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            User account = existing.get();
            if (account.isEmailVerified()) {
                throw new AuthException(
                    HttpStatus.CONFLICT,
                    "email already registered"
                );
            }
            String resendToken = authTokenService.issue(
                account.getId(),
                TokenType.EMAIL_VERIFICATION,
                VERIFICATION_TTL
            );
            sendVerificationQuietly(email, resendToken);
            log.info(
                "re-sent verification for existing unverified email {}",
                email
            );
            return;
        }

        // Logical checks the field-level annotations can't express.
        if (dto.password().equalsIgnoreCase(username)) {
            throw new AuthException(
                HttpStatus.BAD_REQUEST,
                "password cannot be the same as your username"
            );
        }
        if (dto.password().equalsIgnoreCase(email)) {
            throw new AuthException(
                HttpStatus.BAD_REQUEST,
                "password cannot be the same as your email"
            );
        }

        if (userRepository.existsByUsername(username)) {
            throw new AuthException(HttpStatus.CONFLICT, "username taken");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(dto.password()));
        user.setEmailVerified(false);
        user = userRepository.save(user);
        log.info("New user signed up: {} (id={})", username, user.getId());

        String rawToken = authTokenService.issue(
            user.getId(),
            TokenType.EMAIL_VERIFICATION,
            VERIFICATION_TTL
        );
        sendVerificationQuietly(email, rawToken);
    }

    @Transactional
    public LoginResult login(LoginDTO dto) {
        // The identifier may be a username or an email.
        String identifier = dto.username().trim();
        Optional<User> maybeUser = userRepository.findByUsername(identifier);
        if (maybeUser.isEmpty()) {
            maybeUser = userRepository.findByEmail(identifier.toLowerCase());
        }
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
        if (!user.isEmailVerified()) {
            throw new AuthException(
                HttpStatus.FORBIDDEN,
                "Please verify your email before logging in."
            );
        }
        return new LoginResult(createSession(user.getId()), user);
    }

    @Transactional
    public void verifyEmail(VerifyEmailDTO dto) {
        Long userId = authTokenService.consume(
            dto.token(),
            TokenType.EMAIL_VERIFICATION
        );
        User user = userRepository
            .findById(userId)
            .orElseThrow(() ->
                new AuthException(
                    HttpStatus.BAD_REQUEST,
                    "invalid or expired token"
                )
            );
        user.setEmailVerified(true);
    }

    @Transactional
    public void forgotPassword(ForgotPasswordDTO dto) {
        String email = normalizeEmail(dto.email());
        userRepository
            .findByEmail(email)
            .ifPresent(user -> {
                String rawToken = authTokenService.issue(
                    user.getId(),
                    TokenType.PASSWORD_RESET,
                    RESET_TTL
                );
                try {
                    emailService.sendPasswordResetEmail(email, rawToken);
                } catch (Exception e) {
                    log.error("failed to send reset email to {}", email, e);
                }
            });
        // Always returns normally — never reveal whether the email exists.
    }

    @Transactional
    public void resetPassword(ResetPasswordDTO dto) {
        Long userId = authTokenService.consume(
            dto.token(),
            TokenType.PASSWORD_RESET
        );
        User user = userRepository
            .findById(userId)
            .orElseThrow(() ->
                new AuthException(
                    HttpStatus.BAD_REQUEST,
                    "invalid or expired token"
                )
            );
        user.setPasswordHash(passwordEncoder.encode(dto.newPassword()));
        // A reset is a security event: drop every existing session.
        sessionRepository.deleteByUserId(userId);
    }

    @Transactional
    public void resendVerification(ResendVerificationDTO dto) {
        String email = normalizeEmail(dto.email());
        userRepository
            .findByEmail(email)
            .ifPresent(user -> {
                if (!user.isEmailVerified()) {
                    String rawToken = authTokenService.issue(
                        user.getId(),
                        TokenType.EMAIL_VERIFICATION,
                        VERIFICATION_TTL
                    );
                    sendVerificationQuietly(email, rawToken);
                }
            });
        // Always returns normally — never reveal whether the email exists.
    }

    private void sendVerificationQuietly(String email, String rawToken) {
        try {
            emailService.sendVerificationEmail(email, rawToken);
            log.info("verification email handed off to SMTP for {}", email);
        } catch (Exception e) {
            // Account is created regardless; the user can request a resend.
            log.error("failed to send verification email to {}", email, e);
        }
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

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}
