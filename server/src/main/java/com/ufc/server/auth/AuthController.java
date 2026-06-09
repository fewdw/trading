package com.ufc.server.auth;

import com.ufc.server.dto.LoginDTO;
import com.ufc.server.dto.SignupDTO;
import com.ufc.server.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CurrentUserService currentUserService;
    private final SessionRepository sessionRepository;
    private final SignupRateLimiter signupRateLimiter;

    public AuthController(
        AuthService authService,
        CurrentUserService currentUserService,
        SessionRepository sessionRepository,
        SignupRateLimiter signupRateLimiter
    ) {
        this.authService = authService;
        this.currentUserService = currentUserService;
        this.sessionRepository = sessionRepository;
        this.signupRateLimiter = signupRateLimiter;
    }

    /** Create an account and log in immediately, returning a session token. */
    @PostMapping("/signup")
    public ResponseEntity<?> signup(
        @Valid @RequestBody SignupDTO dto,
        HttpServletRequest request
    ) {
        if (!signupRateLimiter.tryAcquire(request)) {
            throw new AuthException(
                HttpStatus.TOO_MANY_REQUESTS,
                "too many sign-ups from here — please try again later"
            );
        }
        AuthService.LoginResult result = authService.signup(dto);
        return ResponseEntity.ok(
            authResponse(result.token(), result.user())
        );
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginDTO dto) {
        AuthService.LoginResult result = authService.login(dto);
        return ResponseEntity.ok(
            authResponse(result.token(), result.user())
        );
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
        body.put("dark_mode", user.isDarkMode());
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

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<?> handleAuth(AuthException e) {
        return ResponseEntity.status(e.getStatus()).body(
            Map.of("error", e.getMessage())
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(
        MethodArgumentNotValidException e
    ) {
        String message = e
            .getBindingResult()
            .getFieldErrors()
            .stream()
            .findFirst()
            .map(fe -> fe.getDefaultMessage())
            .orElse("invalid request");
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    private Map<String, Object> authResponse(String token, User user) {
        Map<String, Object> body = new HashMap<>();
        body.put("token", token);
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", user.getId());
        userMap.put("username", user.getUsername());
        userMap.put("available_coins", user.getAvailableCoins());
        userMap.put("reserved_coins", user.getReservedCoins());
        userMap.put("dark_mode", user.isDarkMode());
        body.put("user", userMap);
        return body;
    }
}
