package com.ufc.server.holding;

import com.ufc.server.ranking.Fighter;
import com.ufc.server.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HoldingRepository extends JpaRepository<Holding, Long> {
    /** The workhorse: a user's position in one fighter (at most one, by constraint). */
    Optional<Holding> findByUserAndFighter(User user, Fighter fighter);

    /** A user's whole portfolio. */
    List<Holding> findByUser(User user);
}
