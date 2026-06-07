package com.ufc.server.tasks;

import com.ufc.server.user.User;
import com.ufc.server.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Ensures the house account exists. The treasury mints the initial shares, posts
 * the seed sell order, and is the sink for the proceeds. It is a normal User so the
 * matching engine needs no special cases. It must never be able to log in and
 * must be excluded from leaderboards.
 */
@Component
@Slf4j
public class TreasurySeederTask {

    public static final String TREASURY_USERNAME = "__TREASURY__";
    public static final long INITIAL_COINS = 1_000_000L * 100L;

    private final UserRepository userRepository;

    public TreasurySeederTask(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Order(1)
    @EventListener(ApplicationReadyEvent.class)
    public void ensureTreasuryExists() {
        if (!userRepository.existsByUsername(TREASURY_USERNAME)) {
            User treasury = new User();
            treasury.setUsername(TREASURY_USERNAME);
            // System account: a reserved, unreachable email and pre-verified so it
            // is never subject to the email-confirmation flow. It can't log in anyway.
            treasury.setEmail("treasury@localhost.invalid");
            treasury.setEmailVerified(true);
            treasury.setPasswordHash("__no_login__");
            treasury.setAvailableCoins(INITIAL_COINS);
            userRepository.save(treasury);
            log.info("created treasury account");
        }
    }
}
