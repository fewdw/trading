package com.ufc.server.order;

import com.ufc.server.ranking.Fighter;
import com.ufc.server.user.User;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
    /** Orders still live on the book (have unfilled quantity). */
    List<OrderStatus> OPEN_STATUSES = List.of(
        OrderStatus.OPEN,
        OrderStatus.PARTIAL
    );

    // --- raw derived queries (don't call these directly; use the wrappers below) ---

    List<Order> findByFighterAndSideAndStatusInOrderByLimitPriceAscCreatedAtAsc(
        Fighter fighter,
        Side side,
        Collection<OrderStatus> statuses
    );

    List<
        Order
    > findByFighterAndSideAndStatusInOrderByLimitPriceDescCreatedAtAsc(
        Fighter fighter,
        Side side,
        Collection<OrderStatus> statuses
    );

    // --- the book, named correctly so the sort/side can't be mismatched ---

    /** Sell side: cheapest first (best ask). Match an incoming BUY against this. */
    default List<Order> findOpenAsks(Fighter fighter) {
        return findByFighterAndSideAndStatusInOrderByLimitPriceAscCreatedAtAsc(
            fighter,
            Side.SELL,
            OPEN_STATUSES
        );
    }

    /** Buy side: highest first (best bid). Match an incoming SELL against this. */
    default List<Order> findOpenBids(Fighter fighter) {
        return findByFighterAndSideAndStatusInOrderByLimitPriceDescCreatedAtAsc(
            fighter,
            Side.BUY,
            OPEN_STATUSES
        );
    }

    /** A user's order history. */
    List<Order> findByUser(User user);
}
