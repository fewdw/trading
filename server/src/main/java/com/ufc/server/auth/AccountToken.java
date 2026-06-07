package com.ufc.server.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * A single-use, time-limited token for email verification or password reset.
 *
 * <p>Only the SHA-256 hash of the token is stored, never the token itself — a DB
 * leak therefore yields nothing usable. The raw token lives only in the email
 * link sent to the user.
 */
@Entity
@Table(
    name = "account_tokens",
    indexes = { @Index(name = "idx_account_token_hash", columnList = "token_hash") }
)
@Getter
@Setter
public class AccountToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TokenType type;

    @Column(nullable = false)
    private Instant expiresAt;

    /** Set when the token is redeemed; a non-null value means it can't be reused. */
    @Column
    private Instant usedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
