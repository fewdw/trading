package com.ufc.server.dto;

/**
 * A user's account value in sub-units (1 coin = 100).
 * {@code netWorth = availableCoins + reservedCoins + holdingsValue} — the figure
 * a leaderboard would rank on.
 */
public record PortfolioSummaryDto(
    long availableCoins,
    long reservedCoins,
    long holdingsValue,
    long netWorth
) {}
