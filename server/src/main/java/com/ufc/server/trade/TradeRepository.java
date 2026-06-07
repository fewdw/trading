package com.ufc.server.trade;

import com.ufc.server.ranking.Fighter;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeRepository extends JpaRepository<Trade, Long> {
    List<Trade> findByFighterOrderByExecutedAtDesc(
        Fighter fighter,
        Pageable pageable
    );
}
