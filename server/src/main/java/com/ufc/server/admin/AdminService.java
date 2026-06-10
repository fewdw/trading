package com.ufc.server.admin;

import com.ufc.server.auth.SessionService;
import com.ufc.server.dto.GiveCoinsDTO;
import com.ufc.server.user.User;
import com.ufc.server.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
public class AdminService {

    /**
     * Unusable password hash for system/agent accounts: they authenticate via an
     * admin-minted session token, never a password login (mirrors the treasury).
     */
    private static final String NO_LOGIN_HASH = "__no_login__";

    private final UserRepository userRepository;
    private final SessionService sessionService;

    public AdminService(
        UserRepository userRepository,
        SessionService sessionService
    ) {
        this.userRepository = userRepository;
        this.sessionService = sessionService;
    }

    /**
     * Credit a user with {@code amount} whole coins. {@code @Transactional} so the
     * loaded entity is managed and the change is flushed on commit. Returns the
     * updated user so the caller can broadcast the new balance.
     */
    @Transactional
    public User giveCoins(GiveCoinsDTO giveCoinsDTO) {
        User user = userRepository
            .findByUsername(giveCoinsDTO.username())
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "User not found"
                )
            );

        // amount is in whole coins; balances are stored in sub-units (1 coin = 100).
        user.setAvailableCoins(
            user.getAvailableCoins() + (long) giveCoinsDTO.amount() * 100
        );
        return user;
    }

    /**
     * Find-or-create an AI agent account and mint a fresh session token for it.
     * Idempotent: re-provisioning an existing agent just issues a new token (used
     * on agent restart or token expiry). New agents start with the standard
     * balance and an unusable password (token-only auth). The {@code isBot} flag
     * makes the leaderboard/profile mark them with a 🤖.
     */
    @Transactional
    public String provisionAgent(String rawUsername) {
        String username = rawUsername.trim();
        User user = userRepository
            .findByUsername(username)
            .orElseGet(() -> {
                User agent = new User();
                agent.setUsername(username);
                agent.setPasswordHash(NO_LOGIN_HASH);
                agent.setAvailableCoins(User.STARTING_COINS);
                agent.setBot(true);
                User saved = userRepository.save(agent);
                log.info("provisioned new AI agent: {} (id={})", username, saved.getId());
                return saved;
            });
        // Ensure the flag is set even for an account that predated the bot rollout.
        if (!user.isBot()) {
            user.setBot(true);
        }
        return sessionService.createSession(user.getId());
    }
}
