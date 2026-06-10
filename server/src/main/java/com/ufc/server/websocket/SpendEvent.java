package com.ufc.server.websocket;

/**
 * A user's total coin balance changed because of a trade — broadcast publicly so
 * anyone viewing that user's profile sees the spend (or income) live.
 *
 * <p>Unlike {@link BalanceUpdateEvent} (private, sent only to the user's own
 * tabs), this carries the {@code username} and the new {@code totalCoins}
 * (available + reserved) so a public profile page can update without auth.
 * {@code delta} is signed: negative when the actor spent coins (a buy filling),
 * positive when they took coins in (a sell filling).
 */
public record SpendEvent(
    Long userId,
    String username,
    long totalCoins,
    long delta,
    String fighterName
) {}
