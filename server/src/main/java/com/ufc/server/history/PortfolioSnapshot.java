package com.ufc.server.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * An hourly point-in-time snapshot of one user's portfolio, recorded by
 * {@code PortfolioSnapshotTask}. Powers the P&amp;L / holdings-value-over-time
 * chart on a profile. Append-only. All money fields are in sub-units (1 coin =
 * 100).
 */
@Entity
@Table(
    name = "portfolio_snapshots",
    indexes = {
        @Index(
            name = "idx_snapshot_user_time",
            columnList = "user_id, captured_at"
        ),
    }
)
@Getter
@Setter
public class PortfolioSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    /** Mark-to-market value of every holding at snapshot time. */
    @Column(nullable = false)
    private long holdingsValue;

    @Column(nullable = false)
    private long realizedPnl;

    @Column(nullable = false)
    private long unrealizedPnl;
}
