package com.ufc.server.tasks;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * The salary schedule in one place, so the payout amount and timing that
 * {@link SalaryTask} actually uses are the same values surfaced to clients
 * (e.g. a profile's "next payout" line) — they can never drift.
 *
 * <p>Salary is paid every {@code salary.interval-days} days at 06:00 in
 * {@code salary.zone} (UTC by default): {@code 1} = daily, {@code 2} = every
 * other day, {@code 14} = every two weeks. The N-day cycle is anchored to the
 * epoch (1970-01-01), so it's deterministic and unaffected by restarts. The
 * amount is in sub-units (1 coin = 100).
 */
@Service
public class SalaryService {

    /** Salary is always paid at this hour, in the configured zone. */
    private static final int PAYOUT_HOUR = 6;

    private final long salaryAmount;
    private final int intervalDays;
    private final ZoneId zone;

    public SalaryService(
        @Value("${salary.amount:50000}") long salaryAmount,
        @Value("${salary.interval-days:14}") int intervalDays,
        @Value("${salary.zone:UTC}") String zone
    ) {
        this.salaryAmount = salaryAmount;
        this.intervalDays = Math.max(1, intervalDays);
        this.zone = ZoneId.of(zone);
    }

    /** Coins paid each payout, in sub-units (1 coin = 100). */
    public long salaryAmount() {
        return salaryAmount;
    }

    /** Whether a payout is due today — the daily task uses this to decide to pay. */
    public boolean isPayoutToday() {
        return isPayoutDay(LocalDate.now(zone));
    }

    /** True when {@code date} falls on the N-day payout cycle. */
    public boolean isPayoutDay(LocalDate date) {
        return Math.floorMod(date.toEpochDay(), intervalDays) == 0;
    }

    /** When the next payout is due: the next cycle day at 06:00 in the configured zone. */
    public Instant nextPayoutAt() {
        ZonedDateTime now = ZonedDateTime.now(zone);
        LocalDate today = now.toLocalDate();
        ZonedDateTime todayPayout = today.atTime(PAYOUT_HOUR, 0).atZone(zone);
        // Today counts only if 06:00 hasn't passed yet.
        if (isPayoutDay(today) && !now.isAfter(todayPayout)) {
            return todayPayout.toInstant();
        }
        for (int i = 1; i <= intervalDays; i++) {
            LocalDate d = today.plusDays(i);
            if (isPayoutDay(d)) {
                return d.atTime(PAYOUT_HOUR, 0).atZone(zone).toInstant();
            }
        }
        return null; // unreachable for intervalDays >= 1
    }
}
