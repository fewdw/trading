package com.ufc.server.tasks;

import com.ufc.server.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pays a flat salary (in coins) to every user.
 *
 * Runs on a fixed wall-clock schedule (1st and 15th of each month at 06:00,
 * UTC by default), not a fixed delay -- so restarting the app no longer hands
 * out an extra payout; it only ever pays at the scheduled times.
 */
@Component
@Slf4j
public class SalaryTask {

    private static final long SALARY = 500;

    private final UserRepository userRepository;

    public SalaryTask(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // Twice a month (~every two weeks). Time zone defaults to UTC; override with
    // salary.cron-zone (e.g. America/Sao_Paulo) and/or salary.cron if needed.
    @Scheduled(
        cron = "${salary.cron:0 0 6 1,15 * *}",
        zone = "${salary.cron-zone:UTC}"
    )
    @Transactional
    public void giveSalary() {
        log.info("Giving salary to all users...");
        int updated = userRepository.addAvailableCoinsToAll(SALARY);
        log.info("Gave {} coins of salary to {} users", SALARY, updated);
    }
}
