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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String TREASURY_USERNAME = "__TREASURY__";

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUserService;

    public AuthController(
        UserRepository userRepository,
        SessionRepository sessionRepository,
        PasswordEncoder passwordEncoder,
        CurrentUserService currentUserService
    ) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentUserService = currentUserService;
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
        log.info(
            "New user signed up: {} (id={})",
            user.getUsername(),
            user.getId()
        );
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
            ) ||
            body.username().equals(TREASURY_USERNAME)
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
        Optional<User> maybeUser = currentUserService.resolveUser(authHeader);
        if (maybeUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                Map.of("error", "unauthorized")
            );
        }
        User user = maybeUser.get();
        Map<String, Object> body = new HashMap<>();
        body.put("id", user.getId());
        body.put("username", user.getUsername());
        body.put("available_coins", user.getAvailableCoins());
        body.put("reserved_coins", user.getReservedCoins());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
        @RequestHeader(
            value = "Authorization",
            required = false
        ) String authHeader
    ) {
        String token = currentUserService.extractToken(authHeader);
        if (token != null) {
            sessionRepository.deleteById(token);
        }
        return ResponseEntity.ok(Map.of("ok", true));
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
        userMap.put("available_coins", user.getAvailableCoins());
        userMap.put("reserved_coins", user.getReservedCoins());
        body.put("user", userMap);
        return body;
    }
}
