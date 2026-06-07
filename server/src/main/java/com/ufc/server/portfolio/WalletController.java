package com.ufc.server.portfolio;

import com.ufc.server.auth.CurrentUserService;
import com.ufc.server.dto.WalletDto;
import com.ufc.server.user.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final PortfolioService portfolioService;
    private final CurrentUserService currentUserService;

    public WalletController(
        PortfolioService portfolioService,
        CurrentUserService currentUserService
    ) {
        this.portfolioService = portfolioService;
        this.currentUserService = currentUserService;
    }

    /** Your coin balances. Same numbers as {@code GET /api/auth/me}. */
    @GetMapping
    public WalletDto getWallet(
        @RequestHeader(
            value = "Authorization",
            required = false
        ) String authHeader
    ) {
        User user = currentUserService.requireUser(authHeader);
        return portfolioService.getWallet(user);
    }
}
