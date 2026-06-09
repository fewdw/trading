package com.ufc.server.dto;

import java.time.Instant;

/**
 * One point on a profile's portfolio-history chart. All money in sub-units
 * (1 coin = 100). Total P&amp;L is {@code realizedPnl + unrealizedPnl}; the
 * client derives it so the two series stay consistent.
 */
public record SnapshotDto(
    Instant capturedAt,
    long holdingsValue,
    long realizedPnl,
    long unrealizedPnl
) {}
