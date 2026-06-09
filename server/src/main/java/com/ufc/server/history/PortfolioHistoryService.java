package com.ufc.server.history;

import com.ufc.server.dto.SnapshotDto;
import com.ufc.server.holding.Holding;
import com.ufc.server.holding.HoldingRepository;
import com.ufc.server.tasks.TreasurySeederTask;
import com.ufc.server.trade.RealizedPnlCalculator;
import com.ufc.server.trade.TradeRepository;
import com.ufc.server.user.User;
import com.ufc.server.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Records and serves hourly portfolio snapshots (holdings value + realized and
 * unrealized P&amp;L) so a profile can chart performance over time.
 */
@Slf4j
@Service
public class PortfolioHistoryService {

    /** How far back a profile's history chart reaches. */
    private static final Duration WINDOW = Duration.ofDays(30);

    private final UserRepository userRepository;
    private final HoldingRepository holdingRepository;
    private final TradeRepository tradeRepository;
    private final PortfolioSnapshotRepository snapshotRepository;

    public PortfolioHistoryService(
        UserRepository userRepository,
        HoldingRepository holdingRepository,
        TradeRepository tradeRepository,
        PortfolioSnapshotRepository snapshotRepository
    ) {
        this.userRepository = userRepository;
        this.holdingRepository = holdingRepository;
        this.tradeRepository = tradeRepository;
        this.snapshotRepository = snapshotRepository;
    }

    /**
     * Snapshot every real user's portfolio at a single shared timestamp (so a
     * given hour lines up across users). Skips the treasury house account.
     */
    @Transactional
    public void recordSnapshots() {
        Instant now = Instant.now();
        int count = 0;
        for (User user : userRepository.findAll()) {
            if (user.getUsername().equals(TreasurySeederTask.TREASURY_USERNAME)) {
                continue;
            }

            long holdingsValue = 0;
            long unrealizedPnl = 0;
            for (Holding h : holdingRepository.findByUser(user)) {
                if (h.getQuantity() <= 0) {
                    continue;
                }
                long lastPrice = h.getFighter().getLastPrice();
                holdingsValue += h.getQuantity() * lastPrice;
                unrealizedPnl += (lastPrice - h.getAveragePrice()) * h.getQuantity();
            }
            long realizedPnl = RealizedPnlCalculator.compute(
                user,
                tradeRepository.findUserTradesChronological(user)
            );

            PortfolioSnapshot snapshot = new PortfolioSnapshot();
            snapshot.setUserId(user.getId());
            snapshot.setCapturedAt(now);
            snapshot.setHoldingsValue(holdingsValue);
            snapshot.setRealizedPnl(realizedPnl);
            snapshot.setUnrealizedPnl(unrealizedPnl);
            snapshotRepository.save(snapshot);
            count++;
        }
        log.info("Recorded {} portfolio snapshots", count);
    }

    /** A user's snapshots over the chart window, oldest first. */
    @Transactional(readOnly = true)
    public List<SnapshotDto> getHistory(String username) {
        User user = userRepository
            .findByUsername(username)
            .filter(u ->
                !u.getUsername().equals(TreasurySeederTask.TREASURY_USERNAME)
            )
            .orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found")
            );

        return snapshotRepository
            .findByUserIdAndCapturedAtGreaterThanEqualOrderByCapturedAtAsc(
                user.getId(),
                Instant.now().minus(WINDOW)
            )
            .stream()
            .map(s ->
                new SnapshotDto(
                    s.getCapturedAt(),
                    s.getHoldingsValue(),
                    s.getRealizedPnl(),
                    s.getUnrealizedPnl()
                )
            )
            .toList();
    }
}
