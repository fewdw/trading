package com.ufc.server.tasks;

import com.ufc.server.auth.SessionRepository;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prunes expired session rows so the {@code sessions} table can't grow without
 * bound. Expired tokens are already rejected on read (see
 * {@code CurrentUserService.resolveToken}), so this is pure housekeeping — it
 * never affects which sessions are valid, only how many dead rows linger.
 *
 * <p>Runs daily at 03:30 UTC, an off-peak hour. The delete is a single
 * range query keyed on {@code expiresAt}, so it stays cheap even on a large
 * table.
 */
@Component
@Slf4j
public class SessionCleanupTask {

    private final SessionRepository sessionRepository;

    public SessionCleanupTask(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "UTC")
    @Transactional
    public void purgeExpiredSessions() {
        long removed = sessionRepository.deleteByExpiresAtBefore(Instant.now());
        if (removed > 0) {
            log.info("Pruned {} expired session(s)", removed);
        }
    }
}
