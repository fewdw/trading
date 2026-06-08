package com.ufc.server.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User {

    public static final long STARTING_COINS = 1000L * 100L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean emailVerified = false;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, columnDefinition = "integer")
    @ColumnDefault("1000")
    private long availableCoins = STARTING_COINS;

    @Column(nullable = false, columnDefinition = "integer")
    @ColumnDefault("0")
    private long reservedCoins = 0;

    /** UI preference: dark mode on/off. Synced to the client for cross-device persistence. */
    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean darkMode = false;

    @Version
    private long version;
}
