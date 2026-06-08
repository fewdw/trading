package com.ufc.server.auth;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Sends the transactional auth emails via Brevo's HTTPS API
 * ({@code https://api.brevo.com/v3/smtp/email}) rather than SMTP — many hosts
 * (Railway included) block outbound SMTP, but HTTPS is always open. Links point
 * at the frontend ({@code app.frontend-url}); the raw token is URL-safe base64
 * so it needs no extra encoding. Send failures throw a {@code RestClientException}
 * — callers decide whether that should surface or be swallowed.
 */
@Service
public class EmailService {

    private static final String BREVO_SEND_ENDPOINT =
        "https://api.brevo.com/v3/smtp/email";

    private final RestClient restClient;
    private final String fromEmail;
    private final String fromName;
    private final String apiKey;
    private final String frontendUrl;

    public EmailService(
        @Value("${app.mail-from}") String fromEmail,
        @Value("${app.mail-from-name:Fighter Market}") String fromName,
        @Value("${brevo.api-key:}") String apiKey,
        @Value("${app.frontend-url}") String frontendUrl
    ) {
        this.fromEmail = fromEmail;
        this.fromName = fromName;
        this.apiKey = apiKey;
        // strip a trailing slash so we don't build "...//verify"
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");

        // Cap connect/read so a slow or unreachable API can't hang the request
        // thread (and, via that, the signup the send is part of).
        SimpleClientHttpRequestFactory requestFactory =
            new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder()
            .baseUrl(BREVO_SEND_ENDPOINT)
            .requestFactory(requestFactory)
            .build();
    }

    public void sendVerificationEmail(String to, String rawToken) {
        String link = frontendUrl + "/verify?token=" + rawToken;
        send(
            to,
            "Confirm your account",
            "Welcome!\n\nConfirm your email address to activate your account:\n\n" +
                link +
                "\n\nThis link expires in 24 hours. If you didn't sign up, you can ignore this email."
        );
    }

    public void sendPasswordResetEmail(String to, String rawToken) {
        String link = frontendUrl + "/reset-password?token=" + rawToken;
        send(
            to,
            "Reset your password",
            "We received a request to reset your password.\n\nUse the link below to choose a new one:\n\n" +
                link +
                "\n\nThis link expires in 1 hour. If you didn't request this, you can safely ignore this email — your password won't change."
        );
    }

    private void send(String to, String subject, String body) {
        Map<String, Object> payload = Map.of(
            "sender", Map.of("email", fromEmail, "name", fromName),
            "to", List.of(Map.of("email", to)),
            "subject", subject,
            "textContent", body
        );
        restClient
            .post()
            .header("api-key", apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body(payload)
            .retrieve()
            .toBodilessEntity();
    }
}
