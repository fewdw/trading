package com.ufc.server.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);

    /** Case-insensitive uniqueness check, so "Bob" and "bob" can't both exist. */
    boolean existsByUsernameIgnoreCase(String username);

    @Modifying
    @Query("UPDATE User u SET u.availableCoins = u.availableCoins + :amount")
    int addAvailableCoinsToAll(long amount);
}
