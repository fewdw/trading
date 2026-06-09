package com.ufc.server.tasks;

import com.ufc.server.history.PortfolioHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Captures an hourly snapshot of every user's portfolio (holdings value +
 * realized/unrealized P&amp;L) so profiles can chart performance over time.
 *
 * <p>Runs on the top of each hour (UTC by default). Override with
 * {@code portfolio.snapshot.cron} / {@code portfolio.snapshot.cron-zone}.
 */
@Component
@Slf4j
public class PortfolioSnapshotTask {

    private final PortfolioHistoryService historyService;

    public PortfolioSnapshotTask(PortfolioHistoryService historyService) {
        this.historyService = historyService;
    }

    @Scheduled(
        cron = "${portfolio.snapshot.cron:0 0 * * * *}",
        zone = "${portfolio.snapshot.cron-zone:UTC}"
    )
    public void capture() {
        log.info("Capturing hourly portfolio snapshots...");
        historyService.recordSnapshots();
    }
}
