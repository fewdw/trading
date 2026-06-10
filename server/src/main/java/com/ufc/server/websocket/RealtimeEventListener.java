package com.ufc.server.websocket;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridges domain events to WebSocket pushes, firing only AFTER the database
 * transaction commits — so clients never refetch and see pre-commit state.
 */
@Component
public class RealtimeEventListener {

    private final BalanceWebSocketHandler handler;

    public RealtimeEventListener(BalanceWebSocketHandler handler) {
        this.handler = handler;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBalanceUpdate(BalanceUpdateEvent event) {
        handler.sendBalanceUpdate(
            event.userId(),
            event.availableCoins(),
            event.reservedCoins()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMarketUpdate(MarketUpdateEvent event) {
        handler.broadcastMarketUpdate(event.fighterId(), event.lastPrice());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSpendUpdate(SpendEvent event) {
        handler.broadcastSpendUpdate(
            event.username(),
            event.totalCoins(),
            event.delta(),
            event.fighterName()
        );
    }
}
