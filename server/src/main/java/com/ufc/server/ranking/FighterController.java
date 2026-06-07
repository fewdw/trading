package com.ufc.server.ranking;

import com.ufc.server.dto.FighterDto;
import com.ufc.server.dto.OrderbookDto;
import com.ufc.server.dto.TradeDto;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Rate limiting is applied globally to /api/** by RateLimitInterceptor.
// These market-data endpoints are intentionally public (no auth).

@RestController
@RequestMapping("/api/fighters")
public class FighterController {

    private final FighterService fighterService;

    public FighterController(FighterService fighterService) {
        this.fighterService = fighterService;
    }

    @GetMapping
    public List<FighterDto> getFighters(
        @RequestParam(required = false) String status
    ) {
        return fighterService.getAllFighters(status);
    }

    @GetMapping("/{fighterId}")
    public FighterDto getFighterById(@PathVariable Long fighterId) {
        return fighterService.getFighterById(fighterId);
    }

    @GetMapping("/{fighterId}/orderbook")
    public OrderbookDto getFighterOrderbookById(@PathVariable Long fighterId) {
        return fighterService.getFighterOrderbook(fighterId);
    }

    @GetMapping("/{fighterId}/trades")
    public List<TradeDto> getFighterTradesById(@PathVariable Long fighterId) {
        return fighterService.getFighterTrades(fighterId);
    }
}
