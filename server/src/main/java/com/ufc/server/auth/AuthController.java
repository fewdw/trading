package com.ufc.server.auth;

import com.ufc.server.user.User;
import com.ufc.server.user.UserRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(
        UserRepository userRepository,
        SessionRepository sessionRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public record Credentials(String username, String password) {}

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody Credentials body) {
        if (
            body == null ||
            body.username() == null ||
            body.password() == null ||
            body.username().isBlank() ||
            body.password().isBlank()
        ) {
            return ResponseEntity.badRequest().body(
                Map.of("error", "username and password required")
            );
        }
        if (userRepository.existsByUsername(body.username())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                Map.of("error", "username taken")
            );
        }
        User user = new User();
        user.setUsername(body.username());
        user.setPasswordHash(passwordEncoder.encode(body.password()));
        user = userRepository.save(user);
        String token = createSession(user.getId());
        return ResponseEntity.ok(authResponse(token, user));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Credentials body) {
        if (
            body == null || body.username() == null || body.password() == null
        ) {
            return ResponseEntity.badRequest().body(
                Map.of("error", "username and password required")
            );
        }
        Optional<User> maybeUser = userRepository.findByUsername(
            body.username()
        );
        if (
            maybeUser.isEmpty() ||
            !passwordEncoder.matches(
                body.password(),
                maybeUser.get().getPasswordHash()
            )
        ) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                Map.of("error", "invalid credentials")
            );
        }
        User user = maybeUser.get();
        String token = createSession(user.getId());
        return ResponseEntity.ok(authResponse(token, user));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(
        @RequestHeader(
            value = "Authorization",
            required = false
        ) String authHeader
    ) {
        Optional<User> user = resolveUser(authHeader);
        if (user.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                Map.of("error", "unauthorized")
            );
        }
        Map<String, Object> body = new HashMap<>();
        body.put("id", user.get().getId());
        body.put("username", user.get().getUsername());
        body.put("coins", user.get().getCoins());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
        @RequestHeader(
            value = "Authorization",
            required = false
        ) String authHeader
    ) {
        String token = extractToken(authHeader);
        if (token != null) {
            sessionRepository.deleteById(token);
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }

    private Optional<User> resolveUser(String authHeader) {
        String token = extractToken(authHeader);
        if (token == null) {
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

    private String extractToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        return authHeader.substring("Bearer ".length()).trim();
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
        session.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        sessionRepository.save(session);
        return token;
    }

    private Map<String, Object> authResponse(String token, User user) {
        Map<String, Object> body = new HashMap<>();
        body.put("token", token);
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", user.getId());
        userMap.put("username", user.getUsername());
        userMap.put("coins", user.getCoins());
        body.put("user", userMap);
        return body;
    }
}
