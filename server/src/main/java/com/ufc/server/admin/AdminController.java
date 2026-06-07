package com.ufc.server.admin;

import com.ufc.server.dto.GiveCoinsDTO;
import com.ufc.server.user.User;
import com.ufc.server.websocket.BalanceWebSocketHandler;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final AdminAuthService adminAuthService;
    private final BalanceWebSocketHandler balanceWebSocketHandler;

    public AdminController(
        AdminService adminService,
        AdminAuthService adminAuthService,
        BalanceWebSocketHandler balanceWebSocketHandler
    ) {
        this.adminService = adminService;
        this.adminAuthService = adminAuthService;
        this.balanceWebSocketHandler = balanceWebSocketHandler;
    }

    @PostMapping("/coins")
    public ResponseEntity<Void> giveCoins(
        @RequestHeader(
            value = "X-Admin-Api-Key",
            required = false
        ) String apiKey,
        @Valid @RequestBody GiveCoinsDTO giveCoinsDTO
    ) {
        adminAuthService.requireAdmin(apiKey); // 401/503 unless a valid admin key

        // The grant is committed here, then pushed live to the user's tabs.
        User updated = adminService.giveCoins(giveCoinsDTO);
        balanceWebSocketHandler.sendBalanceUpdate(
            updated.getId(),
            updated.getAvailableCoins(),
            updated.getReservedCoins()
        );
        return ResponseEntity.ok().build();
    }
}
