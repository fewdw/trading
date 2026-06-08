package com.ufc.server.dto;

import com.ufc.server.order.OrderSide;
import java.time.Instant;

/** One trade from a user's perspective, for their public trade history. */
public record UserTradeDto(
    Long fighterId,
    String fighterName,
    OrderSide side,
    long price,
    long quantity,
    Instant executedAt
) {}
