package com.ufc.server.order;

import com.ufc.server.ranking.Fighter;
import com.ufc.server.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

/** A buy or sell order. Open orders collectively form the order book. */
@Entity
@Table(
    name = "orders",
    indexes = {
        @Index(
            name = "idx_order_book",
            columnList = "fighter_id, side, status, limit_price, created_at"
        ),
    }
)
@Getter
@Setter
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fighter_id", nullable = false)
    private Fighter fighter;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private OrderSide side;

    @Column(name = "order_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private OrderType type;

    /** Price in sub-units (1 coin = 100). Null for MARKET orders. */
    @Column(name = "limit_price")
    private Long limitPrice;

    @Column(nullable = false)
    private long quantity;

    @Column(nullable = false)
    private long filledQuantity;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Optimistic-lock guard. Two orders matching against this same resting order
     * concurrently both bump its version; the second to commit fails and rolls
     * back, so a resting order can never be filled past its quantity (no
     * overselling). {@code @ColumnDefault} backfills existing rows when the
     * column is added.
     */
    @Version
    @ColumnDefault("0")
    @Column(nullable = false)
    private long version;
}
