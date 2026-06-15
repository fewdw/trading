package com.ufc.server.websocket;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** Exposes the live-updates socket at {@code /ws}. */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final BalanceWebSocketHandler balanceWebSocketHandler;
    private final String[] allowedOrigins;

    public WebSocketConfig(
        BalanceWebSocketHandler balanceWebSocketHandler,
        @Value("${websocket.allowed-origins:*}") String allowedOrigins
    ) {
        this.balanceWebSocketHandler = balanceWebSocketHandler;
        this.allowedOrigins = parseOrigins(allowedOrigins);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Auth is the httpOnly `session` cookie (same-origin) or a `?token=`
        // handshake param (cross-origin); unauthenticated sockets stay open but
        // only ever receive public market broadcasts. Origins are restricted to
        // the configured frontend in production (WS_ALLOWED_ORIGINS); the default
        // "*" preserves the original open behavior for local dev / when unset.
        registry
            .addHandler(balanceWebSocketHandler, "/ws")
            .setAllowedOrigins(allowedOrigins);
    }

    /** Comma-separated origins to an array; blank or "*" means allow any origin. */
    private static String[] parseOrigins(String raw) {
        if (raw == null || raw.isBlank() || raw.trim().equals("*")) {
            return new String[] { "*" };
        }
        String[] origins = Arrays.stream(raw.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toArray(String[]::new);
        return origins.length == 0 ? new String[] { "*" } : origins;
    }
}
