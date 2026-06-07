package com.ufc.server.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends the transactional auth emails. Links point at the frontend
 * ({@code app.frontend-url}); the raw token is URL-safe base64 so it needs no
 * extra encoding. Send failures throw {@code MailException} — callers decide
 * whether that should surface or be swallowed.
 */
@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String from;
    private final String frontendUrl;

    public EmailService(
        JavaMailSender mailSender,
        @Value("${app.mail-from}") String from,
        @Value("${app.frontend-url}") String frontendUrl
    ) {
        this.mailSender = mailSender;
        this.from = from;
        // strip a trailing slash so we don't build "...//verify"
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
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
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}
