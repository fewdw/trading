package com.ufc.server.profile;

import com.ufc.server.dto.ProfileDto;
import com.ufc.server.dto.SnapshotDto;
import com.ufc.server.history.PortfolioHistoryService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public, read-only user profiles. No auth required. */
@RestController
@RequestMapping("/api/users")
public class ProfileController {

    private final ProfileService profileService;
    private final PortfolioHistoryService historyService;

    public ProfileController(
        ProfileService profileService,
        PortfolioHistoryService historyService
    ) {
        this.profileService = profileService;
        this.historyService = historyService;
    }

    @GetMapping("/{username}/profile")
    public ProfileDto getProfile(@PathVariable String username) {
        return profileService.getProfile(username);
    }

    /** Hourly portfolio history (holdings value + P&L) for the chart. */
    @GetMapping("/{username}/history")
    public List<SnapshotDto> getHistory(@PathVariable String username) {
        return historyService.getHistory(username);
    }
}
