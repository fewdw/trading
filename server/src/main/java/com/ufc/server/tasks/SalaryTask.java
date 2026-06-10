package com.ufc.server.tasks;

import com.ufc.server.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pays a flat salary (in sub-units) to every user.
 *
 * <p>The job fires daily at 06:00 (zone from {@code salary.zone}) but only pays
 * on the configured N-day cycle (see {@link SalaryService}). Anchoring to a
 * fixed wall-clock time plus a date-based cycle means restarting the app never
 * hands out an extra payout — it only ever pays once per cycle day.
 */
@Component
@Slf4j
public class SalaryTask {

    private final UserRepository userRepository;
    private final SalaryService salaryService;

    public SalaryTask(UserRepository userRepository, SalaryService salaryService) {
        this.userRepository = userRepository;
        this.salaryService = salaryService;
    }

    @Scheduled(cron = "0 0 6 * * *", zone = "${salary.zone:UTC}")
    @Transactional
    public void giveSalary() {
        if (!salaryService.isPayoutToday()) {
            return; // not a payout day on the N-day cycle
        }
        long amount = salaryService.salaryAmount();
        int updated = userRepository.addAvailableCoinsToAll(amount);
        log.info("Gave {} sub-units of salary to {} users", amount, updated);
    }
}
