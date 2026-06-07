package com.ufc.server.websocket;

/** A user's coin balances changed — pushed to that user's open tabs. */
public record BalanceUpdateEvent(
    Long userId,
    long availableCoins,
    long reservedCoins
) {}
