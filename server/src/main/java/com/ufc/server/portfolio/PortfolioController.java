package com.ufc.server.portfolio;

import com.ufc.server.auth.CurrentUserService;
import com.ufc.server.dto.PortfolioSummaryDto;
import com.ufc.server.dto.PositionDto;
import com.ufc.server.user.User;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portfolio")
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final CurrentUserService currentUserService;

    public PortfolioController(
        PortfolioService portfolioService,
        CurrentUserService currentUserService
    ) {
        this.portfolioService = portfolioService;
        this.currentUserService = currentUserService;
    }

    /** Your holdings, each valued and marked to market. */
    @GetMapping
    public List<PositionDto> getPortfolio(
        @RequestHeader(
            value = "Authorization",
            required = false
        ) String authHeader
    ) {
        User user = currentUserService.requireUser(authHeader);
        return portfolioService.getPortfolio(user);
    }

    /** Your net worth: cash + reserved + the value of every holding. */
    @GetMapping("/summary")
    public PortfolioSummaryDto getSummary(
        @RequestHeader(
            value = "Authorization",
            required = false
        ) String authHeader
    ) {
        User user = currentUserService.requireUser(authHeader);
        return portfolioService.getSummary(user);
    }
}
