package com.ufc.server.trade;

import com.ufc.server.ranking.Fighter;
import com.ufc.server.user.User;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TradeRepository extends JpaRepository<Trade, Long> {
    List<Trade> findByFighterOrderByExecutedAtDesc(
        Fighter fighter,
        Pageable pageable
    );

    /** Every trade a user took part in (buyer or seller), oldest first. */
    @Query(
        "select t from Trade t " +
        "where t.buyOrder.user = :user or t.sellOrder.user = :user " +
        "order by t.executedAt asc"
    )
    List<Trade> findUserTradesChronological(@Param("user") User user);
}
