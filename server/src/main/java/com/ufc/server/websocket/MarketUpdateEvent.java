package com.ufc.server.websocket;

/** A fighter's book/price changed — broadcast so open fighter pages can refresh. */
public record MarketUpdateEvent(Long fighterId, long lastPrice) {}
