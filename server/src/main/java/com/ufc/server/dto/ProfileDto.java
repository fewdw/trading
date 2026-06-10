package com.ufc.server.dto;

import java.time.Instant;
import java.util.List;

/**
 * A user's public profile. All money in sub-units (1 coin = 100).
 * {@code realizedPnl} is computed by replaying the user's trades with
 * average-cost accounting (matching how holdings track average price).
 *
 * <p>{@code trades} are executed fills; {@code orders} is the user's order
 * history across every status (open/partial/filled/cancelled), so pending and
 * cancelled buys and sells are visible alongside the trade tape.
 *
 * <p>{@code nextPayoutAt}/{@code payoutAmount} describe the recurring salary
 * every user receives, computed from the same schedule the payout task runs on.
 */
public record ProfileDto(
    String username,
    boolean isBot,
    long realizedPnl,
    long unrealizedPnl,
    long holdingsValue,
    Instant nextPayoutAt,
    long payoutAmount,
    List<PositionDto> holdings,
    List<UserTradeDto> trades,
    List<OrderDto> orders
) {}
