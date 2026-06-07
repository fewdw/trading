package com.ufc.server.admin;

import com.ufc.server.dto.GiveCoinsDTO;
import com.ufc.server.user.User;
import com.ufc.server.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminService {

    private final UserRepository userRepository;

    public AdminService(UserRepository userRepository) {
        this.userRepository = userRepository;
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
}
