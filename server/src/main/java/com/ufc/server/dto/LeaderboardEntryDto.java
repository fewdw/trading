package com.ufc.server.dto;

/**
 * One row of the top-holders leaderboard. {@code rank} is 1-based;
 * {@code holdingsValue} is the user's mark-to-market portfolio value in
 * sub-units (1 coin = 100).
 */
public record LeaderboardEntryDto(
    int rank,
    String username,
    long holdingsValue
) {}
