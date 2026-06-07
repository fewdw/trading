package com.ufc.server.auth;

import com.ufc.server.dto.ForgotPasswordDTO;
import com.ufc.server.dto.LoginDTO;
import com.ufc.server.dto.ResendVerificationDTO;
import com.ufc.server.dto.ResetPasswordDTO;
import com.ufc.server.dto.SignupDTO;
import com.ufc.server.dto.VerifyEmailDTO;
import com.ufc.server.user.User;
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

    public AuthController(
        AuthService authService,
        CurrentUserService currentUserService,
        SessionRepository sessionRepository
    ) {
        this.authService = authService;
        this.currentUserService = currentUserService;
        this.sessionRepository = sessionRepository;
    }

    /** Create an account (unverified) and email a confirmation link. No session is issued yet. */
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@Valid @RequestBody SignupDTO dto) {
        authService.signup(dto);
        return ResponseEntity.ok(
            Map.of(
                "message",
                "Account created. Check your email to confirm your account before logging in."
            )
        );
    }

    /** Confirm an email address from the link in the verification email. */
    @PostMapping("/verify")
    public ResponseEntity<?> verify(@Valid @RequestBody VerifyEmailDTO dto) {
        authService.verifyEmail(dto);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** Resend the verification email. Always succeeds (no account enumeration). */
    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification(
        @Valid @RequestBody ResendVerificationDTO dto
    ) {
        authService.resendVerification(dto);
        return ResponseEntity.ok(
            Map.of(
                "message",
                "If an account exists and is unverified, a new verification link has been sent."
            )
        );
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginDTO dto) {
        AuthService.LoginResult result = authService.login(dto);
        return ResponseEntity.ok(
            authResponse(result.token(), result.user())
        );
    }

    /** Start the password-reset flow. Always succeeds (no account enumeration). */
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(
        @Valid @RequestBody ForgotPasswordDTO dto
    ) {
        authService.forgotPassword(dto);
        return ResponseEntity.ok(
            Map.of(
                "message",
                "If an account exists for that email, a password reset link has been sent."
            )
        );
    }

    /** Finish the password-reset flow with the token from the email. */
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(
        @Valid @RequestBody ResetPasswordDTO dto
    ) {
        authService.resetPassword(dto);
        return ResponseEntity.ok(Map.of("ok", true));
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
        body.put("email", user.getEmail());
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
        userMap.put("email", user.getEmail());
        userMap.put("available_coins", user.getAvailableCoins());
        userMap.put("reserved_coins", user.getReservedCoins());
        body.put("user", userMap);
        return body;
    }
}
