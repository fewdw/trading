package com.ufc.server.profile;

import com.ufc.server.dto.OrderDto;
import com.ufc.server.dto.PositionDto;
import com.ufc.server.dto.ProfileDto;
import com.ufc.server.dto.UserTradeDto;
import com.ufc.server.holding.Holding;
import com.ufc.server.holding.HoldingRepository;
import com.ufc.server.order.Order;
import com.ufc.server.order.OrderRepository;
import com.ufc.server.order.OrderSide;
import com.ufc.server.ranking.Fighter;
import com.ufc.server.tasks.SalaryService;
import com.ufc.server.tasks.TreasurySeederTask;
import com.ufc.server.trade.Trade;
import com.ufc.server.trade.TradeRepository;
import com.ufc.server.user.User;
import com.ufc.server.user.UserRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Builds a user's public profile: holdings, P&L, and trade history. */
@Service
public class ProfileService {

    private static final int MAX_HISTORY = 100;

    private final UserRepository userRepository;
    private final HoldingRepository holdingRepository;
    private final TradeRepository tradeRepository;
    private final OrderRepository orderRepository;
    private final SalaryService salaryService;

    public ProfileService(
        UserRepository userRepository,
        HoldingRepository holdingRepository,
        TradeRepository tradeRepository,
        OrderRepository orderRepository,
        SalaryService salaryService
    ) {
        this.userRepository = userRepository;
        this.holdingRepository = holdingRepository;
        this.tradeRepository = tradeRepository;
        this.orderRepository = orderRepository;
        this.salaryService = salaryService;
    }

    @Transactional(readOnly = true)
    public ProfileDto getProfile(String username) {
        User user = userRepository
            .findByUsername(username)
            .filter(u ->
                !u.getUsername().equals(TreasurySeederTask.TREASURY_USERNAME)
            )
            .orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found")
            );

        List<PositionDto> holdings = holdingRepository
            .findByUser(user)
            .stream()
            .filter(h -> h.getQuantity() > 0)
            .map(this::toPosition)
            .toList();
        long unrealizedPnl = holdings
            .stream()
            .mapToLong(PositionDto::unrealizedPnl)
            .sum();
        long holdingsValue = holdings
            .stream()
            .mapToLong(PositionDto::marketValue)
            .sum();

        List<Trade> trades = tradeRepository.findUserTradesChronological(user);
        long realizedPnl = computeRealizedPnl(user, trades);

        return new ProfileDto(
            user.getUsername(),
            realizedPnl,
            unrealizedPnl,
            holdingsValue,
            salaryService.nextPayoutAt(),
            salaryService.salaryAmount(),
            holdings,
            history(user, trades),
            orders(user)
        );
    }

    /**
     * The user's order history newest-first, capped at {@link #MAX_HISTORY}.
     * Every status is included so pending (OPEN/PARTIAL) and CANCELLED buys and
     * sells show up next to filled ones.
     */
    private List<OrderDto> orders(User user) {
        return orderRepository
            .findByUser(user)
            .stream()
            .sorted(Comparator.comparing(Order::getCreatedAt).reversed())
            .limit(MAX_HISTORY)
            .map(OrderDto::from)
            .toList();
    }

    /**
     * Replay the user's trades in order, tracking a running average cost per
     * fighter. Each sell realizes {@code (price - avgCost) * qty} — the same
     * average-cost basis the matching engine uses for holdings.
     */
    private long computeRealizedPnl(User user, List<Trade> trades) {
        Map<Long, long[]> state = new HashMap<>(); // fighterId -> [qty, avgCost]
        long realized = 0;
        for (Trade t : trades) {
            long fighterId = t.getFighter().getId();
            long price = t.getPrice();
            long qty = t.getQuantity();
            long[] s = state.computeIfAbsent(fighterId, k -> new long[] { 0, 0 });

            if (isBuyer(user, t)) {
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

    /** Trade history newest-first, capped at {@link #MAX_HISTORY}. */
    private List<UserTradeDto> history(User user, List<Trade> trades) {
        List<UserTradeDto> history = new ArrayList<>(trades.size());
        for (Trade t : trades) {
            history.add(
                new UserTradeDto(
                    t.getFighter().getId(),
                    t.getFighter().getName(),
                    isBuyer(user, t) ? OrderSide.BUY : OrderSide.SELL,
                    t.getPrice(),
                    t.getQuantity(),
                    t.getExecutedAt()
                )
            );
        }
        Collections.reverse(history);
        return history.size() > MAX_HISTORY
            ? history.subList(0, MAX_HISTORY)
            : history;
    }

    private boolean isBuyer(User user, Trade trade) {
        return trade.getBuyOrder().getUser().getId().equals(user.getId());
    }

    private PositionDto toPosition(Holding h) {
        Fighter fighter = h.getFighter();
        long lastPrice = fighter.getLastPrice();
        long marketValue = h.getQuantity() * lastPrice;
        long unrealizedPnl = (lastPrice - h.getAveragePrice()) * h.getQuantity();
        return new PositionDto(
            fighter.getId(),
            fighter.getName(),
            fighter.getPhoto(),
            h.getQuantity(),
            h.getReservedQuantity(),
            h.getAveragePrice(),
            lastPrice,
            marketValue,
            unrealizedPnl
        );
    }
}
