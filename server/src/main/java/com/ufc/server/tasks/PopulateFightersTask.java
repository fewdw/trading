package com.ufc.server.tasks;

import com.ufc.server.ranking.Fighter;
import com.ufc.server.ranking.FighterRepository;
import com.ufc.server.ranking.Status;
import com.ufc.server.user.UserRepository;
import com.ufc.server.user.UserService;
import java.text.Normalizer;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Pulls fighter data from the scraper service and stores it.
 *
 * Refreshes once a week (Mondays at 06:00, UTC by default). On startup it also
 * seeds the data once if the table is still empty, so the app isn't blank until
 * the first scheduled run. A failure (e.g. the scraper being offline) is logged
 * and swallowed so it never takes the application down.
 */
@Component
@Slf4j
public class PopulateFightersTask {

    /** One entry in the /fighters payload. */
    public record FighterDto(String name, String photo) {}

    private static final ParameterizedTypeReference<
        List<FighterDto>
    > FIGHTERS_TYPE = new ParameterizedTypeReference<>() {};

    private final RestClient scraper;
    private final FighterRepository fighterRepository;
    private final UserService userService;

    public PopulateFightersTask(
        @Value("${scraper.base-url}") String scraperBaseUrl,
        FighterRepository fighterRepository,
        UserRepository userRepository,
        UserService userService
    ) {
        this.scraper = RestClient.create(scraperBaseUrl);
        this.fighterRepository = fighterRepository;
        this.userService = userService;
    }

    // Weekly: Mondays at 06:00. Time zone defaults to UTC; override with
    // scraper.cron-zone (e.g. America/Sao_Paulo) and/or scraper.cron if needed.
    @Scheduled(
        cron = "${scraper.cron:0 0 6 * * MON}",
        zone = "${scraper.cron-zone:UTC}"
    )
    public void refresh() {
        try {
            int fighters = populateFighters();
            log.info("fighter data refreshed: {} fighters", fighters);
        } catch (Exception e) {
            log.warn("failed to refresh fighter data: {}", e.toString());
        }
    }

    // Seed once at startup only when there's nothing stored yet, so we don't
    // sit empty until the first Monday. No-op once data exists.
    @Order(2)
    @EventListener(ApplicationReadyEvent.class)
    public void seedIfEmpty() {
        if (fighterRepository.count() == 0) {
            log.info(
                "no fighter data found at startup -- seeding from scraper"
            );
            refresh();
        }
    }

    private int populateFighters() {
        List<FighterDto> incoming = scraper
            .get()
            .uri("/fighters")
            .retrieve()
            .body(FIGHTERS_TYPE);

        for (FighterDto f : incoming) {
            String name = normalize(f.name());

            if (!fighterRepository.existsByName(name)) {
                Fighter fighter = new Fighter();
                fighter.setName(name);
                fighter.setPhoto(f.photo());
                fighter.setStatus(Status.ACTIVE);
                fighter.setLastPrice(1250);
                fighterRepository.save(fighter);
                log.info("adding fighter: {}", name);
                if (fighter.getPhoto() == null) {
                    log.warn("missing photo for fighter: {}", name);
                }
            }
        }

        userService.seedNewFightersToTreasury();

        return incoming.size();
    }

    private static String normalize(String raw) {
        String stripped = Normalizer.normalize(
            raw.trim(),
            Normalizer.Form.NFD
        ).replaceAll("\\p{M}+", "");
        return stripped.toLowerCase();
    }
}
