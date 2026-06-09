// k6 load test for the matching engine's order-placement path.
//
// Each virtual user repeatedly POSTs a resting limit buy to /api/orders, which
// exercises the full hot path: auth, reserve funds, walk the book, persist.
// k6 reports throughput (orders/sec) and latency percentiles (incl. P99).
//
// Run (no install needed — via Docker), against a backend started with rate
// limits relaxed (see loadtest/README.md):
//
//   docker run --rm -i --add-host=host.docker.internal:host-gateway \
//     -e BASE_URL=http://host.docker.internal:8080 -e VUS=50 -e DURATION=30s \
//     grafana/k6 run - < loadtest/place-orders.js
//
// Tunables (env): BASE_URL, USERS, VUS, DURATION.

import http from "k6/http";
import { check, fail } from "k6";
import { Counter, Trend } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const USERS = parseInt(__ENV.USERS || "25", 10);
const VUS = parseInt(__ENV.VUS || "50", 10);
const DURATION = __ENV.DURATION || "30s";

const ordersPlaced = new Counter("orders_placed");
const orderLatency = new Trend("order_latency", true);

export const options = {
  scenarios: {
    place_orders: {
      executor: "constant-vus",
      vus: VUS,
      duration: DURATION,
    },
  },
  // P99 isn't in k6's default summary stats — ask for it explicitly.
  summaryTrendStats: ["avg", "min", "med", "p(90)", "p(95)", "p(99)", "max"],
};

const JSON_HEADERS = { "Content-Type": "application/json" };

// Create a pool of accounts and resolve a tradable fighter, once.
export function setup() {
  const runId = Date.now();
  const tokens = [];
  for (let i = 0; i < USERS; i++) {
    const res = http.post(
      `${BASE_URL}/api/auth/signup`,
      JSON.stringify({
        username: `bench_${runId}_${i}`,
        password: "benchPassw0rd",
      }),
      { headers: JSON_HEADERS },
    );
    if (res.status === 200) {
      tokens.push(res.json("token"));
    }
  }
  if (tokens.length === 0) {
    fail(
      "no accounts created — is the signup rate limit relaxed? " +
        "(set SIGNUP_RATELIMIT_BURST/PER_HOUR high; see loadtest/README.md)",
    );
  }

  const fightersRes = http.get(`${BASE_URL}/api/fighters`);
  const fighters = fightersRes.json();
  const fighter =
    (fighters || []).find((f) => f.status === "ACTIVE") || (fighters || [])[0];
  if (!fighter) {
    fail("no fighters available — seed the market before load testing.");
  }

  return { tokens, fighterId: String(fighter.id) };
}

export default function (data) {
  const token = data.tokens[(__VU - 1) % data.tokens.length];
  // A limit buy priced far below the market rests on the book (never crosses),
  // so each call is a clean, repeatable placement that won't deplete funds.
  const res = http.post(
    `${BASE_URL}/api/orders`,
    JSON.stringify({
      fighterId: data.fighterId,
      side: "BUY",
      type: "LIMIT",
      limitPrice: 1,
      quantity: 1,
    }),
    {
      headers: { ...JSON_HEADERS, Authorization: `Bearer ${token}` },
      tags: { name: "placeOrder" },
    },
  );

  ordersPlaced.add(1);
  orderLatency.add(res.timings.duration);
  check(res, { "order accepted (201)": (r) => r.status === 201 });
}

export function handleSummary(data) {
  const placed = data.metrics.orders_placed?.values?.count ?? 0;
  const rate = data.metrics.orders_placed?.values?.rate ?? 0; // per second
  const lat = data.metrics.order_latency?.values ?? {};
  const ok = data.metrics.checks?.values?.rate ?? 0;

  const line = "─".repeat(46);
  const out =
    `\n${line}\n` +
    `  Matching engine — order placement load test\n` +
    `${line}\n` +
    `  orders placed:     ${placed}\n` +
    `  throughput:        ${rate.toFixed(0)} orders/sec\n` +
    `  latency  avg:      ${(lat.avg ?? 0).toFixed(1)} ms\n` +
    `  latency  p95:      ${(lat["p(95)"] ?? 0).toFixed(1)} ms\n` +
    `  latency  p99:      ${(lat["p(99)"] ?? 0).toFixed(1)} ms\n` +
    `  latency  max:      ${(lat.max ?? 0).toFixed(1)} ms\n` +
    `  accepted (201):    ${(ok * 100).toFixed(2)} %\n` +
    `${line}\n`;

  return { stdout: out };
}
