package com.ufc.server.history;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioSnapshotRepository
    extends JpaRepository<PortfolioSnapshot, Long> {
    /** One user's snapshots since a cutoff, oldest first (chart-ready order). */
    List<PortfolioSnapshot> findByUserIdAndCapturedAtGreaterThanEqualOrderByCapturedAtAsc(
        Long userId,
        Instant since
    );
}
