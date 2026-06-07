package com.ufc.server.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** Exposes the live-updates socket at {@code /ws}. */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final BalanceWebSocketHandler balanceWebSocketHandler;

    public WebSocketConfig(BalanceWebSocketHandler balanceWebSocketHandler) {
        this.balanceWebSocketHandler = balanceWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Auth is the httpOnly session cookie sent on the handshake, so any
        // origin may attempt to connect; unauthenticated sockets are closed.
        registry.addHandler(balanceWebSocketHandler, "/ws").setAllowedOrigins("*");
    }
}
