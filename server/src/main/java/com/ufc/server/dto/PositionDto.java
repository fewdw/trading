package com.ufc.server.dto;

/**
 * One position in a user's portfolio. All money fields are in sub-units
 * (1 coin = 100). {@code marketValue} and {@code unrealizedPnl} are derived
 * from the fighter's current {@code lastPrice}.
 */
public record PositionDto(
    Long fighterId,
    String fighterName,
    String photo,
    long quantity,
    long reservedQuantity,
    long averagePrice,
    long lastPrice,
    long marketValue,
    long unrealizedPnl
) {}
