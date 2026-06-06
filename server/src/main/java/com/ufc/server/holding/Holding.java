package com.ufc.server.holding;

import com.ufc.server.ranking.Fighter;
import com.ufc.server.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/** user_id + fighter_id combination must be unique */
@Entity
@Table(
    name = "holdings",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_holding_user_fighter",
        columnNames = { "user_id", "fighter_id" }
    )
)
@Getter
@Setter
public class Holding {

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
    private long quantity;

    @Column(nullable = false)
    private long reservedQuantity;

    @Column(nullable = false)
    private long averagePrice;

    @Version
    private long version;
}
