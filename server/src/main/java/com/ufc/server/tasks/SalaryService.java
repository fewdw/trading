package com.ufc.server.tasks;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

/**
 * The salary schedule in one place, so the payout amount and timing that
 * {@link SalaryTask} actually uses are the same values surfaced to clients
 * (e.g. a profile's "next payout" line). Reads the very same
 * {@code salary.cron} / {@code salary.cron-zone} properties the task's
 * {@code @Scheduled} cron uses, so the displayed date can never drift from when
 * the money is really paid.
 */
@Service
public class SalaryService {

    /** Flat salary paid to every user each payout, in sub-units (1 coin = 100). */
    public static final long SALARY = 500 * 100L;

    private final String cron;
    private final ZoneId zone;

    public SalaryService(
        @Value("${salary.cron:0 0 6 1,15 * *}") String cron,
        @Value("${salary.cron-zone:UTC}") String zone
    ) {
        this.cron = cron;
        this.zone = ZoneId.of(zone);
    }

    /** Coins paid each payout, in sub-units. */
    public long salaryAmount() {
        return SALARY;
    }

    /** When the next salary payout is due, per the configured cron (or null if none). */
    public Instant nextPayoutAt() {
        ZonedDateTime next = CronExpression.parse(cron).next(
            ZonedDateTime.now(zone)
        );
        return next != null ? next.toInstant() : null;
    }
}
