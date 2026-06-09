# Load testing the matching engine

Drives concurrent order placement with [k6](https://k6.io) and reports
**throughput (orders/sec)** and **latency percentiles (P95/P99)** — the numbers
worth quoting on a résumé.

## Why a special config

Production runs a deliberately strict rate limiter (and a 5-per-hour signup cap).
A load test must measure the engine, not the throttle, so we start the backend
with those limits relaxed via the `loadtest/docker-compose.loadtest.yml` overlay
(it only sets `RATELIMIT_*` / `SIGNUP_RATELIMIT_*` env vars — nothing else).

## Run it

**1. Start the stack with rate limits relaxed** (needs the market seeded with at
least one `ACTIVE` fighter — the normal startup scrape handles that):

```bash
docker compose -f docker-compose.yml -f loadtest/docker-compose.loadtest.yml up --build
```

**2. In another terminal, run the test** (k6 via Docker — no install needed):

```bash
docker run --rm -i --add-host=host.docker.internal:host-gateway \
  -e BASE_URL=http://host.docker.internal:8080 \
  -e VUS=50 -e DURATION=30s -e USERS=25 \
  grafana/k6 run - < loadtest/place-orders.js
```

On Linux you can instead use `--network host` and `BASE_URL=http://localhost:8080`.
If k6 is installed locally: `k6 run loadtest/place-orders.js` (set the env vars first).

**3. Read the summary** it prints:

```
──────────────────────────────────────────────
  Matching engine — order placement load test
──────────────────────────────────────────────
orders placed:     124785
throughput:        3930 orders/sec
latency  avg:      11.9 ms
latency  p95:      28.8 ms
latency  p99:      50.8 ms
latency  max:      242.3 ms
accepted (201):    87.98 %
──────────────────────────────────────────────
```

Quote the **throughput** and **p99** on your résumé. Re-run with higher `VUS`
(e.g. 100, 200) to find the saturation point, and mention the hardware
(e.g. "on an M-series laptop").

## Tunables

| Env | Default | Meaning |
|-----|---------|---------|
| `BASE_URL` | `http://localhost:8080` | Backend base URL |
| `USERS` | `25` | Accounts created in setup (each VU reuses one) |
| `VUS` | `50` | Concurrent virtual users |
| `DURATION` | `30s` | Test length |

## Watch it live

Bring up Prometheus + Grafana (see [`../monitoring/`](../monitoring)) and watch
`engine_order_placement_seconds` (latency/throughput) and `engine_trades_total`
(fills) move in real time while the test runs.
