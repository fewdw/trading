package com.ufc.server.dto;

import java.util.List;

/**
 * A user's public profile. All money in sub-units (1 coin = 100).
 * {@code realizedPnl} is computed by replaying the user's trades with
 * average-cost accounting (matching how holdings track average price).
 */
public record ProfileDto(
    String username,
    long realizedPnl,
    long unrealizedPnl,
    long holdingsValue,
    List<PositionDto> holdings,
    List<UserTradeDto> trades
) {}
