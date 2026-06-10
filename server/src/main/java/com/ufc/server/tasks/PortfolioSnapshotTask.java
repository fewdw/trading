package com.ufc.server.tasks;

import com.ufc.server.history.PortfolioHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Captures periodic snapshots of every user's portfolio (holdings value +
 * realized/unrealized P&amp;L) so profiles can chart performance over time.
 *
 * <p>Frequency is configured by {@code portfolio.snapshot.per-hour} (env var
 * {@code PORTFOLIO_SNAPSHOTS_PER_HOUR}): {@code 1} = once an hour, {@code 2} =
 * every 30 minutes, {@code 4} = every 15 minutes. The first run happens one
 * interval after startup.
 */
@Component
@Slf4j
public class PortfolioSnapshotTask {

    private final PortfolioHistoryService historyService;

    public PortfolioSnapshotTask(PortfolioHistoryService historyService) {
        this.historyService = historyService;
    }

    // Interval (ms) = one hour / snapshots-per-hour. SpEL derives it from the
    // property; the guard falls back to hourly if it's set to 0 or less. Using
    // an initial delay of one interval avoids a snapshot firing during boot.
    @Scheduled(
        fixedRateString = "#{${portfolio.snapshot.per-hour:1} > 0 ? 3600000 / ${portfolio.snapshot.per-hour:1} : 3600000}",
        initialDelayString = "#{${portfolio.snapshot.per-hour:1} > 0 ? 3600000 / ${portfolio.snapshot.per-hour:1} : 3600000}"
    )
    public void capture() {
        log.info("Capturing portfolio snapshots...");
        historyService.recordSnapshots();
    }
}
