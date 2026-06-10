# Fighter Market

[![CI](https://github.com/fewdw/trading/actions/workflows/ci.yml/badge.svg)](https://github.com/fewdw/trading/actions/workflows/ci.yml)

A real-time fantasy stock market where the "stocks" are UFC fighters. You start
with 1,000 coins, buy and sell shares of fighters through a real order book, and
your net worth moves with the market. **[Live demo →](https://client-production-2c9e.up.railway.app)**

It started as an excuse to build the part most trading demos fake: an actual
**limit-order matching engine**. There are no mocked prices here — every price
is the result of a real trade between two orders.

**Stack:** Spring Boot (Java) · Postgres · Next.js (React) · two Python services
· Docker — deployed as five services on Railway.

## How it fits together

Five moving parts, one database:

- **`server/`** — the Spring Boot backend: the matching engine, the REST API, and a WebSocket for live updates.
- **`client/`** — the Next.js frontend.
- **`scrape/`** — a small Flask scraper that pulls the current UFC roster and photos.
- **`agents/`** — Python AI traders (more below) that keep the market liquid.
- **Postgres** — the single source of truth, *including* the live order book.

## The matching engine

This is the heart of the project. Orders match continuously on **price-time
priority** (best price first, oldest first at a given price), the way a real
exchange works. A few decisions I'm happy with:

- **Money is always integers.** Everything is stored in "sub-units" (1 coin = 100), so no floating-point rounding ever creeps into a balance.
- **The order book lives in Postgres,** not in memory. Resting orders are just rows; matching is an indexed query ordered by price then time. The book survives restarts for free and there's exactly one source of truth.
- **Funds are reserved, not assumed.** A buy reserves coins; a sell reserves shares. You can't spend the same coin twice, even with orders resting on the book.
- **Concurrency is optimistic.** Every balance / holding / order row is versioned (`@Version`); two fills that touch the same row trigger a quick retry instead of a lock.
- **You can't trade with yourself** — an incoming order skips your own resting orders.

None of that is taken on faith. A property-test suite hammers the engine and
asserts coins and shares are *conserved* — nothing created or destroyed — plus a
concurrency test on the optimistic-locking path, all against a real Postgres via
Testcontainers.

## Seeding a market from nothing

A brand-new market has no sellers, so nothing can trade. To beat the cold start,
a **treasury house account** mints a fixed number of shares for each new fighter
and lists them at an IPO price — instant liquidity to trade against. The seed
price and share count are env vars, so you can launch a market with whatever
shape you like.

## The AI traders

An empty market is boring, so ~10 autonomous **AI agents** trade on their own.
The fun constraint: they aren't special. Each agent is an ordinary account that
trades through the **exact same public API and engine** as a human — they just
show up on the leaderboard with a 🤖.

The design is **hybrid**, because asking an LLM to place every single order is
slow and expensive:

- **Gemini sets the strategy** every few minutes. Given a persona (momentum trader, contrarian, market maker…) and a market snapshot, it returns a structured directive: bullish/bearish, which fighters, how aggressive.
- **Deterministic code executes** that directive every ~30s, inside hard risk limits the model can't break (never overspend, never short, capped position sizes).
- **If Gemini is slow or down, they keep trading** on a heuristic fallback — the market never stalls on an API hiccup.

Flip the whole thing off with `RUN_WITH_AI=0`.

## Live and configurable

Prices and balances stream to the browser over a **WebSocket**, pushed the
instant the database transaction commits, so the order book and your wallet
update without a refresh. Scheduled jobs pay a recurring salary and snapshot
everyone's portfolio for the P&L chart — and the knobs that matter (seed
price/shares, salary amount and frequency, snapshot rate, AI on/off) are all env
vars.

## Run it

```bash
docker compose up --build      # → http://localhost:3000
```

restart db
```bash
docker compose down -v && docker compose build --no-cache && docker compose up
```

Drop a `GEMINI_API_KEY` into `.env` for real AI behavior (without one the agents
fall back to a heuristic). Reset everything with
`docker compose down -v && docker compose up --build`.

Full setup and a Railway deploy walkthrough are in
[`DEPLOY-RAILWAY.md`](DEPLOY-RAILWAY.md); the REST API is documented in
[`API-DOCUMENTATION.md`](API-DOCUMENTATION.md).

## Tests, metrics, load

- **Tests** — `cd server && ./mvnw test` runs the invariant + concurrency suite (needs Docker). CI runs it on every push.
- **Observability** — Actuator + Micrometer expose Prometheus metrics at `/actuator/prometheus`, including custom engine metrics (placement latency/throughput, fills, rejects, live WebSocket connections). A turnkey Prometheus + Grafana stack lives in [`monitoring/`](monitoring).
- **Load** — [`loadtest/`](loadtest) drives concurrent orders with k6: ~3,930 orders/sec, p99 ~50.8 ms.

![Grafana dashboard showing live matching-engine metrics scraped from Prometheus](grafana.png)
