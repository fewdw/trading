# Monitoring (Prometheus + Grafana)

The backend exposes Micrometer metrics at `/actuator/prometheus`, including custom
matching-engine metrics. This folder is a turnkey Prometheus + Grafana stack to
scrape and chart them (great for a dashboard screenshot in the README).

## Run

```bash
# 1. start the app (from the repo root)
docker compose up --build            # or: cd server && ./mvnw spring-boot:run

# 2. start monitoring
docker compose -f monitoring/docker-compose.yml up
```

- Grafana → http://localhost:3001 (anonymous admin, no login)
- Prometheus → http://localhost:9090
- Raw metrics → http://localhost:9091/actuator/prometheus (actuator's own port)

In Grafana the Prometheus datasource is pre-provisioned. Create a dashboard and
add panels with the queries below, then drive traffic (see
[`../loadtest/`](../loadtest)) to watch them move.

## Custom metrics & panel queries

| Panel | PromQL |
|-------|--------|
| Order throughput (orders/sec) | `sum(rate(engine_order_placement_seconds_count[1m]))` |
| Order latency P99 (ms) | `histogram_quantile(0.99, sum(rate(engine_order_placement_seconds_bucket[5m])) by (le)) * 1000` |
| Order latency P95 (ms) | `histogram_quantile(0.95, sum(rate(engine_order_placement_seconds_bucket[5m])) by (le)) * 1000` |
| Fills/sec | `sum(rate(engine_trades_total[1m]))` |
| Rejections/sec | `sum(rate(engine_order_rejected_total[1m]))` |
| Active WebSocket connections | `websocket_connections_active` |

Spring Boot/Micrometer also ship JVM, HTTP, HikariCP and Postgres metrics out of
the box — e.g. `http_server_requests_seconds_count`, `jvm_memory_used_bytes`,
`hikaricp_connections_active`.

## Source of the custom metrics

| Metric | Where |
|--------|-------|
| `engine.order.placement` (timer) | `OrderService.placeOrder` |
| `engine.trades` (counter) | `OrderService.executeTrade` |
| `engine.order.rejected` (counter) | `OrderService.placeOrder` (on rejection) |
| `websocket.connections.active` (gauge) | `BalanceWebSocketHandler` |
