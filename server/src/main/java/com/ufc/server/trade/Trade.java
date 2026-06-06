package com.ufc.server.trade;

import com.ufc.server.order.Order;
import com.ufc.server.ranking.Fighter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/** One match between a buy and a sell order. Append-only: never updated. */
@Entity
@Table(
    name = "trades",
    indexes = {
        @Index(
            name = "idx_trade_fighter_time",
            columnList = "fighter_id, executed_at"
        ),
    }
)
@Getter
@Setter
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fighter_id", nullable = false)
    private Fighter fighter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buy_order_id", nullable = false)
    private Order buyOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sell_order_id", nullable = false)
    private Order sellOrder;

    /** Execution price in sub-units. */
    @Column(nullable = false)
    private long price;

    @Column(nullable = false)
    private long quantity;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant executedAt;
}
