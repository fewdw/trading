package com.ufc.server.dto;

/**
 * Coin balances in sub-units (1 coin = 100). {@code reservedCoins} are locked
 * against open buy orders; {@code totalCoins = availableCoins + reservedCoins}.
 */
public record WalletDto(
    long availableCoins,
    long reservedCoins,
    long totalCoins
) {}
