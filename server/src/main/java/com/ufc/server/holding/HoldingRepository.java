package com.ufc.server.holding;

import com.ufc.server.ranking.Fighter;
import com.ufc.server.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HoldingRepository extends JpaRepository<Holding, Long> {
    /** The workhorse: a user's position in one fighter (at most one, by constraint). */
    Optional<Holding> findByUserAndFighter(User user, Fighter fighter);

    /** A user's whole portfolio. */
    List<Holding> findByUser(User user);

    /**
     * Top holders by mark-to-market holdings value, excluding one username (the
     * treasury house account). Each row is {@code [userId, username, value]};
     * {@code value} is read via {@link Number} to stay portable across the
     * database-specific return type of {@code sum(...)}. Use a {@link Pageable}
     * to cap the result (e.g. top 10).
     */
    @Query(
        "select u.id, u.username, sum(h.quantity * f.lastPrice) " +
        "from Holding h join h.user u join h.fighter f " +
        "where h.quantity > 0 and u.username <> :exclude " +
        "group by u.id, u.username " +
        "order by sum(h.quantity * f.lastPrice) desc"
    )
    List<Object[]> findTopHolders(
        @Param("exclude") String exclude,
        Pageable pageable
    );
}
