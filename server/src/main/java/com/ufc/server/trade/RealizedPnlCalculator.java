package com.ufc.server.trade;

import com.ufc.server.user.User;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Average-cost realized P&L from a user's chronological trade tape.
 *
 * <p>Replays each trade tracking a running average cost per fighter; every sell
 * realizes {@code (price - avgCost) * qty} — the same average-cost basis the
 * matching engine uses for holdings. Shared by the live profile view and the
 * hourly portfolio snapshot so the two can never disagree.
 *
 * <p>Call inside a transaction: it walks the lazy {@code buyOrder.user}
 * association to tell whether the user was the buyer on each trade.
 */
public final class RealizedPnlCalculator {

    private RealizedPnlCalculator() {}

    /** Trades must be chronological (oldest first). All money in sub-units. */
    public static long compute(User user, List<Trade> trades) {
        Map<Long, long[]> state = new HashMap<>(); // fighterId -> [qty, avgCost]
        long realized = 0;
        for (Trade t : trades) {
            long fighterId = t.getFighter().getId();
            long price = t.getPrice();
            long qty = t.getQuantity();
            long[] s = state.computeIfAbsent(fighterId, k -> new long[] { 0, 0 });

            if (t.getBuyOrder().getUser().getId().equals(user.getId())) {
                long newQty = s[0] + qty;
                long newBasis = s[0] * s[1] + qty * price;
                s[1] = newQty > 0 ? newBasis / newQty : 0;
                s[0] = newQty;
            } else {
                realized += (price - s[1]) * qty;
                s[0] = Math.max(0, s[0] - qty);
            }
        }
        return realized;
    }
}
