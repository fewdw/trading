package com.ufc.server.portfolio;

import com.ufc.server.dto.PortfolioSummaryDto;
import com.ufc.server.dto.PositionDto;
import com.ufc.server.dto.WalletDto;
import com.ufc.server.holding.Holding;
import com.ufc.server.holding.HoldingRepository;
import com.ufc.server.ranking.Fighter;
import com.ufc.server.user.User;
import com.ufc.server.user.UserRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Read-only views over a user's positions and cash. Everything is built inside a
 * transaction because holdings reference the fighter lazily (open-in-view=false)
 * and we read {@code fighter.lastPrice} to value the book.
 */
@Service
public class PortfolioService {

    private final UserRepository userRepository;
    private final HoldingRepository holdingRepository;

    public PortfolioService(
        UserRepository userRepository,
        HoldingRepository holdingRepository
    ) {
        this.userRepository = userRepository;
        this.holdingRepository = holdingRepository;
    }

    /** Every position the user still holds, valued at the current last price. */
    @Transactional(readOnly = true)
    public List<PositionDto> getPortfolio(User caller) {
        User actor = requireActor(caller);
        return holdingRepository
            .findByUser(actor)
            .stream()
            .filter(h -> h.getQuantity() > 0) // hide fully-exited positions
            .map(this::toPosition)
            .toList();
    }

    /** Cash + the mark-to-market value of every holding = net worth. */
    @Transactional(readOnly = true)
    public PortfolioSummaryDto getSummary(User caller) {
        User actor = requireActor(caller);
        long holdingsValue = holdingRepository
            .findByUser(actor)
            .stream()
            .mapToLong(h -> h.getQuantity() * h.getFighter().getLastPrice())
            .sum();
        long available = actor.getAvailableCoins();
        long reserved = actor.getReservedCoins();
        return new PortfolioSummaryDto(
            available,
            reserved,
            holdingsValue,
            available + reserved + holdingsValue
        );
    }

    /** Just the coin balances. */
    @Transactional(readOnly = true)
    public WalletDto getWallet(User caller) {
        User actor = requireActor(caller);
        return new WalletDto(
            actor.getAvailableCoins(),
            actor.getReservedCoins(),
            actor.getAvailableCoins() + actor.getReservedCoins()
        );
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

    /** Reload the caller as a managed entity inside the current transaction. */
    private User requireActor(User caller) {
        return userRepository
            .findById(caller.getId())
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "unauthorized"
                )
            );
    }
}
