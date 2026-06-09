# Fighter Market

[![CI](https://github.com/fewdw/trading/actions/workflows/ci.yml/badge.svg)](https://github.com/fewdw/trading/actions/workflows/ci.yml)

A real-time fantasy stock market for UFC fighters: a custom limit-order matching
engine (market/limit orders, price-time priority, an aggregated order book) with
live price and balance updates over WebSockets.

**Stack:** Spring Boot (Java) · Postgres · Next.js (React) · Python scraper · Docker.

## Run

```bash
docker compose up --build
```

Reset the database:

```bash
docker compose down -v && docker compose build --no-cache && docker compose up
```

## Tests

The server suite includes property/invariant tests of the matching engine
(conservation of shares and coins, price-time priority, self-trade prevention)
and a concurrency test of the optimistic-locking path, all run against a real
Postgres via Testcontainers (needs Docker running):

```bash
cd server && ./mvnw test
```

CI (GitHub Actions) builds the server + client, runs this suite, and lints the
client on every push.
