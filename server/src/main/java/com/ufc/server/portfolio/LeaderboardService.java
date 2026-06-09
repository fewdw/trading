package com.ufc.server.portfolio;

import com.ufc.server.dto.LeaderboardEntryDto;
import com.ufc.server.holding.HoldingRepository;
import com.ufc.server.tasks.TreasurySeederTask;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ranks users by mark-to-market holdings value for the public leaderboard. */
@Service
public class LeaderboardService {

    /** Only the top N holders are shown. */
    private static final int TOP_N = 10;

    private final HoldingRepository holdingRepository;

    public LeaderboardService(HoldingRepository holdingRepository) {
        this.holdingRepository = holdingRepository;
    }

    /** The top {@value #TOP_N} holders, highest value first. */
    @Transactional(readOnly = true)
    public List<LeaderboardEntryDto> topHolders() {
        List<Object[]> rows = holdingRepository.findTopHolders(
            TreasurySeederTask.TREASURY_USERNAME,
            PageRequest.of(0, TOP_N)
        );
        List<LeaderboardEntryDto> entries = new ArrayList<>(rows.size());
        int rank = 1;
        for (Object[] row : rows) {
            String username = (String) row[1];
            long value = ((Number) row[2]).longValue();
            entries.add(new LeaderboardEntryDto(rank++, username, value));
        }
        return entries;
    }
}
