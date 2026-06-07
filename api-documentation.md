# UFC Stock Market — API Documentation

REST API for the fighter trading engine. Fighters are the tradable instruments
("stocks"); users trade shares of them with coins via a continuous
price/time-priority order book.

## Conventions

- **Base URL:** `http://localhost:8080`
- **All money is in sub-units: `1 coin = 100 sub-units`.** A `limitPrice` of `150`
  means 1.50 coins. New users start with `100000` sub-units (1000 coins).
- **Content type:** `application/json` for all request and response bodies.
- **Auth:** session-token bearer auth. Send `Authorization: Bearer <token>` on
  every authenticated endpoint. Tokens come from `signup`/`login` and last 7 days.
- **Timestamps:** ISO-8601 UTC (e.g. `2026-06-07T12:34:56.789Z`).

### Error shape

Two shapes appear depending on the endpoint:

Auth endpoints, the rate limiter, and the orders conflict handler return:

```json
{ "error": "human readable message" }
```

Everything else throws framework errors rendered as (with `include-message=always`):

```json
{
  "timestamp": "2026-06-07T12:34:56.789+00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "insufficient funds",
  "path": "/api/orders"
}
```

In both cases the **HTTP status code is the contract**; the message is for humans.

### Rate limiting

Every `/api/**` endpoint is rate limited by a token bucket:

- **~600 requests/minute sustained, burst of 300.**
- Bucketed **per session token** when authenticated, otherwise **per IP**.
- On exceed: **`429 Too Many Requests`**, a `Retry-After` header (seconds), and:

```json
{ "error": "rate limit exceeded", "retryAfterSeconds": 5 }
```

---

## Authentication — `/api/auth`

### POST `/api/auth/signup`

Create an account and start a session.

**Request**
```json
{ "username": "conor", "password": "hunter2" }
```

**Response `200 OK`**
```json
{
  "token": "Hh3k...base64url",
  "user": {
    "id": 1,
    "username": "conor",
    "available_coins": 100000,
    "reserved_coins": 0
  }
}
```

| Code | When |
|------|------|
| `200` | Created, session issued |
| `400` | `username and password required` (missing/blank) |
| `409` | `username taken` |

---

### POST `/api/auth/login`

**Request**
```json
{ "username": "conor", "password": "hunter2" }
```

**Response `200 OK`** — same shape as signup.

| Code | When |
|------|------|
| `200` | Authenticated, session issued |
| `400` | `username and password required` |
| `401` | `invalid credentials` |

---

### GET `/api/auth/me`

Current user. **Auth required.**

**Response `200 OK`**
```json
{ "id": 1, "username": "conor", "available_coins": 98500, "reserved_coins": 1500 }
```

| Code | When |
|------|------|
| `200` | OK |
| `401` | Missing/expired/invalid token |

---

### POST `/api/auth/logout`

Invalidate the current session token. **Auth required (token in header).**

**Response `200 OK`**
```json
{ "ok": true }
```

---

## Market data — `/api/fighters` (public, no auth)

### GET `/api/fighters`

List fighters. Optional `?status=` filter.

**Query params**

| Param | Type | Notes |
|-------|------|-------|
| `status` | string | One of `UNLISTED`, `IPO`, `ACTIVE`, `DELISTING_PENDING`, `LIQUIDATED`. Omit for all. |

**Response `200 OK`**
```json
[
  { "id": 1, "name": "Jon Jones", "photo": "https://...", "status": "ACTIVE", "lastPrice": 150 }
]
```

| Code | When |
|------|------|
| `200` | OK |
| `400` | `Unknown status: <x>` |

---

### GET `/api/fighters/{fighterId}`

Single fighter.

**Response `200 OK`**
```json
{ "id": 1, "name": "Jon Jones", "photo": "https://...", "status": "ACTIVE", "lastPrice": 150 }
```

| Code | When |
|------|------|
| `200` | OK |
| `404` | `Fighter not found: <id>` |

---

### GET `/api/fighters/{fighterId}/orderbook`

Aggregated depth. `bids` are highest-price-first, `asks` lowest-first; `quantity`
is total remaining (unfilled) shares at that price level.

**Response `200 OK`**
```json
{
  "bids": [ { "price": 149, "quantity": 30 }, { "price": 148, "quantity": 12 } ],
  "asks": [ { "price": 151, "quantity": 20 }, { "price": 152, "quantity": 40 } ]
}
```

| Code | When |
|------|------|
| `200` | OK |
| `404` | Fighter not found |

---

### GET `/api/fighters/{fighterId}/trades`

Recent trade tape (latest 50, newest first).

**Response `200 OK`**
```json
[ { "price": 150, "quantity": 5, "executedAt": "2026-06-07T12:34:56.789Z" } ]
```

| Code | When |
|------|------|
| `200` | OK |
| `404` | Fighter not found |

---

## Trading — `/api/orders` (auth required)

### POST `/api/orders`

Place a buy or sell order. It reserves coins/shares, then matches against the
book immediately (price/time priority). A `LIMIT` remainder rests on the book; a
`MARKET` remainder is cancelled.

**Request**
```json
{
  "fighterId": "1",
  "side": "BUY",
  "type": "LIMIT",
  "limitPrice": 150,
  "quantity": 10
}
```

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `fighterId` | string | yes | Numeric id of the fighter |
| `side` | enum | yes | `BUY` or `SELL` |
| `type` | enum | yes | `LIMIT` or `MARKET` |
| `limitPrice` | number (sub-units) | LIMIT only | **Required for `LIMIT`, must be omitted for `MARKET`.** Positive. |
| `quantity` | integer | yes | Positive |

**Response `201 Created`**
```json
{
  "id": 42,
  "fighterId": 1,
  "fighterName": "Jon Jones",
  "side": "BUY",
  "type": "LIMIT",
  "limitPrice": 150,
  "quantity": 10,
  "filledQuantity": 4,
  "status": "PARTIAL",
  "createdAt": "2026-06-07T12:34:56.789Z"
}
```

`status` is one of `OPEN`, `PARTIAL`, `FILLED`, `CANCELLED`. Inspect
`filledQuantity` to see how much executed immediately.

> **Self-trade prevention.** Your order never matches against your own resting
> orders — they are skipped, and your order matches other participants instead
> (any remainder rests on the book as normal). A consequence is that you may
> simultaneously hold a resting bid and ask that cross each other; they will
> never execute against one another.

| Code | When |
|------|------|
| `201` | Order accepted (may be fully/partly filled, resting, or — for a market order with no liquidity — `CANCELLED`) |
| `400` | Validation failure (`@Valid`), `invalid fighterId`, `insufficient funds`, `insufficient shares`, or limit/market price rule violated |
| `401` | Not authenticated |
| `404` | `fighter not found: <id>` |
| `409` | `fighter is not tradable` (status ≠ `ACTIVE`), or a concurrent fill conflict — retry |

---

### DELETE `/api/orders/{orderId}`

Cancel one of **your** open or partially-filled orders. Releases the reservation
on the unfilled remainder (coins for a buy, shares for a sell).

**Response `200 OK`** — the cancelled order as an `OrderDto` (`status: "CANCELLED"`).

| Code | When |
|------|------|
| `200` | Cancelled |
| `401` | Not authenticated |
| `404` | `order not found` (missing, or not owned by you) |
| `409` | `order is not open` (already filled/cancelled) |

---

### GET `/api/orders`

List **your** orders, newest first. Optional `?status=` filter.

**Query params**

| Param | Type | Notes |
|-------|------|-------|
| `status` | string | `open` = still on the book (`OPEN` + `PARTIAL`); or an exact status `OPEN` / `PARTIAL` / `FILLED` / `CANCELLED`. Omit for all. |

**Response `200 OK`** — array of `OrderDto` (same shape as the POST response).

| Code | When |
|------|------|
| `200` | OK |
| `400` | `unknown status: <x>` |
| `401` | Not authenticated |

---

### GET `/api/orders/{orderId}`

Fetch one of **your** orders.

**Response `200 OK`** — a single `OrderDto`.

| Code | When |
|------|------|
| `200` | OK |
| `401` | Not authenticated |
| `404` | `order not found` (missing, or not owned by you) |

---

## Portfolio & wallet (auth required)

### GET `/api/portfolio`

Your positions, each valued at the fighter's current `lastPrice`. Fully-exited
positions (quantity 0) are omitted.

**Response `200 OK`**
```json
[
  {
    "fighterId": 1,
    "fighterName": "Jon Jones",
    "photo": "https://...",
    "quantity": 10,
    "reservedQuantity": 4,
    "averagePrice": 140,
    "lastPrice": 150,
    "marketValue": 1500,
    "unrealizedPnl": 100
  }
]
```

| Field | Meaning |
|-------|---------|
| `quantity` | Total shares owned |
| `reservedQuantity` | Shares locked in open sell orders |
| `averagePrice` | Weighted-average cost basis (sub-units) |
| `lastPrice` | Fighter's current price (sub-units) |
| `marketValue` | `quantity × lastPrice` |
| `unrealizedPnl` | `(lastPrice − averagePrice) × quantity` |

| Code | When |
|------|------|
| `200` | OK |
| `401` | Not authenticated |

---

### GET `/api/portfolio/summary`

Net worth — the leaderboard number.

**Response `200 OK`**
```json
{ "availableCoins": 98500, "reservedCoins": 1500, "holdingsValue": 1500, "netWorth": 101500 }
```

| Field | Meaning |
|-------|---------|
| `availableCoins` | Free cash |
| `reservedCoins` | Cash locked in open buy orders |
| `holdingsValue` | `Σ(quantity × lastPrice)` across all holdings |
| `netWorth` | `availableCoins + reservedCoins + holdingsValue` |

| Code | When |
|------|------|
| `200` | OK |
| `401` | Not authenticated |

---

### GET `/api/wallet`

Coin balances only. (Same numbers as `GET /api/auth/me`, different shape.)

**Response `200 OK`**
```json
{ "availableCoins": 98500, "reservedCoins": 1500, "totalCoins": 100000 }
```

| Code | When |
|------|------|
| `200` | OK |
| `401` | Not authenticated |

---

## Quick reference

| Method | Path | Auth | Purpose |
|--------|------|:----:|---------|
| POST | `/api/auth/signup` | – | Create account |
| POST | `/api/auth/login` | – | Log in |
| GET | `/api/auth/me` | ✓ | Current user |
| POST | `/api/auth/logout` | ✓ | End session |
| GET | `/api/fighters` | – | List fighters |
| GET | `/api/fighters/{id}` | – | Fighter detail |
| GET | `/api/fighters/{id}/orderbook` | – | Aggregated book |
| GET | `/api/fighters/{id}/trades` | – | Recent trades |
| POST | `/api/orders` | ✓ | Place buy/sell order |
| DELETE | `/api/orders/{id}` | ✓ | Cancel order |
| GET | `/api/orders` | ✓ | List your orders |
| GET | `/api/orders/{id}` | ✓ | One order |
| GET | `/api/portfolio` | ✓ | Holdings + P&L |
| GET | `/api/portfolio/summary` | ✓ | Net worth |
| GET | `/api/wallet` | ✓ | Coin balances |

## Example: a full trade flow

```bash
# 1. sign up, capture the token
TOKEN=$(curl -s -X POST localhost:8080/api/auth/signup \
  -H 'Content-Type: application/json' \
  -d '{"username":"conor","password":"hunter2"}' | jq -r .token)

# 2. browse the market
curl -s localhost:8080/api/fighters

# 3. place a limit buy for 10 shares of fighter 1 at 1.50 coins
curl -s -X POST localhost:8080/api/orders \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"fighterId":"1","side":"BUY","type":"LIMIT","limitPrice":150,"quantity":10}'

# 4. check your positions and net worth
curl -s localhost:8080/api/portfolio        -H "Authorization: Bearer $TOKEN"
curl -s localhost:8080/api/portfolio/summary -H "Authorization: Bearer $TOKEN"
```
