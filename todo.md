## Tier 2 — Depth differentiators

- Idempotency keys on order placement — prevents double-submits; a real fintech concern and a clean thing to discuss.
- Audit trail / event log of every order lifecycle transition — leans into the "financial system" credibility.
- Caching layer (Redis) for hot reads (fighter list, order book) with explicit invalidation — classic interview topic.
- DB depth: document your indexes, prove you've eliminated N+1s (you've got open-in-view=false already — good), explain optimistic-locking retry strategy.

---
## Tier 3 — Flashy features

- A market-maker / bot that posts liquidity so the demo isn't empty (also makes your load test realistic and your charts non-flat).
- More order types (stop-loss), candlestick OHLC charts, portfolio analytics (Sharpe-ish, win rate).
- Auth hardening you can name: you already added rate-limited signup, weak-password blocking, case-insensitive uniqueness — extend with refresh tokens or 2FA if you want an "auth" bullet.
- A short architecture diagram + "interesting engineering" section in the README. Recruiters skim; one diagram + 3 bullets of "the hard parts" punches above its weight.
