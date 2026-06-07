package com.ufc.server.websocket;

import com.ufc.server.auth.CurrentUserService;
import com.ufc.server.user.User;
import java.io.IOException;
import java.net.URI;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Pushes live balance updates to a user's open browser tabs.
 *
 * <p>The socket authenticates with the same httpOnly {@code session} cookie the
 * REST API uses — the browser sends it automatically on the handshake — so the
 * token never has to be exposed to client-side JavaScript. (A {@code ?token=}
 * query param is also accepted as a fallback for non-browser clients/testing.)
 * Sockets that don't resolve to a user are closed immediately.
 */
@Component
public class BalanceWebSocketHandler extends TextWebSocketHandler {

    private static final String SESSION_COOKIE = "session";
    private static final String USER_ID_ATTR = "userId";

    private final CurrentUserService currentUserService;

    /** userId -> that user's live sockets (one per open tab). */
    private final ConcurrentHashMap<Long, Set<WebSocketSession>> sessions =
        new ConcurrentHashMap<>();

    public BalanceWebSocketHandler(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session)
        throws Exception {
        Long userId = resolveUserId(session);
        if (userId == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        session.getAttributes().put(USER_ID_ATTR, userId);
        sessions
            .computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet())
            .add(session);
    }

    @Override
    public void afterConnectionClosed(
        WebSocketSession session,
        CloseStatus status
    ) {
        Object userId = session.getAttributes().get(USER_ID_ATTR);
        if (userId instanceof Long id) {
            Set<WebSocketSession> userSessions = sessions.get(id);
            if (userSessions != null) {
                userSessions.remove(session);
            }
        }
    }

    /** Send a fresh balance to every tab the user has open. No-op if none. */
    public void sendBalanceUpdate(
        Long userId,
        long availableCoins,
        long reservedCoins
    ) {
        Set<WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions == null || userSessions.isEmpty()) {
            return;
        }
        TextMessage message = new TextMessage(
            "{\"type\":\"BALANCE_UPDATE\",\"availableCoins\":" +
                availableCoins +
                ",\"reservedCoins\":" +
                reservedCoins +
                "}"
        );
        for (WebSocketSession session : userSessions) {
            try {
                // WebSocketSession is not safe for concurrent sends.
                synchronized (session) {
                    if (session.isOpen()) {
                        session.sendMessage(message);
                    }
                }
            } catch (IOException e) {
                // Broken pipe etc. — drop it; close handler will clean up.
            }
        }
    }

    /** Broadcast a market change to every connected client so open pages can refresh. */
    public void broadcastMarketUpdate(Long fighterId, long lastPrice) {
        TextMessage message = new TextMessage(
            "{\"type\":\"MARKET_UPDATE\",\"fighterId\":" +
            fighterId +
            ",\"lastPrice\":" +
            lastPrice +
            "}"
        );
        for (Set<WebSocketSession> userSessions : sessions.values()) {
            for (WebSocketSession session : userSessions) {
                try {
                    synchronized (session) {
                        if (session.isOpen()) {
                            session.sendMessage(message);
                        }
                    }
                } catch (IOException e) {
                    // Broken pipe etc. — drop it; close handler will clean up.
                }
            }
        }
    }

    private Long resolveUserId(WebSocketSession session) {
        String token = tokenFromCookies(session);
        if (token == null) {
            token = tokenFromQuery(session);
        }
        return currentUserService
            .resolveToken(token)
            .map(User::getId)
            .orElse(null);
    }

    private String tokenFromCookies(WebSocketSession session) {
        String cookieHeader = session.getHandshakeHeaders().getFirst("cookie");
        if (cookieHeader == null) {
            return null;
        }
        for (String part : cookieHeader.split(";")) {
            String cookie = part.trim();
            if (cookie.startsWith(SESSION_COOKIE + "=")) {
                return cookie.substring((SESSION_COOKIE + "=").length());
            }
        }
        return null;
    }

    private String tokenFromQuery(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null || uri.getQuery() == null) {
            return null;
        }
        for (String param : uri.getQuery().split("&")) {
            int eq = param.indexOf('=');
            if (eq > 0 && param.substring(0, eq).equals("token")) {
                return param.substring(eq + 1);
            }
        }
        return null;
    }
}
