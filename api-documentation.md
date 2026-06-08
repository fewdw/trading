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

Tokens for email confirmation and password reset are single-use, time-limited,
and stored only as SHA-256 hashes; the raw token lives only in the emailed link.

### POST `/api/auth/signup`

Create an account. It starts **unverified** and a confirmation email is sent —
**no session is issued** until the email is confirmed.

**Request**
```json
{ "email": "conor@example.com", "username": "conor", "password": "hunter2pw" }
```

| Field | Rules |
|-------|-------|
| `email` | valid email, unique |
| `username` | 3–30 chars, unique |
| `password` | 8–72 chars |

**Response `200 OK`**
```json
{ "message": "Account created. Check your email to confirm your account before logging in." }
```

| Code | When |
|------|------|
| `200` | Created; confirmation email sent |
| `400` | Validation failure (message names the field) |
| `409` | `username taken` or `email already registered` |

---

### POST `/api/auth/verify`

Confirm an email address using the token from the confirmation email.

**Request** → `{ "token": "<token from the email link>" }`

**Response `200 OK`** → `{ "ok": true }`

| Code | When |
|------|------|
| `200` | Email confirmed |
| `400` | `invalid or expired token` (includes already-used) |

---

### POST `/api/auth/resend-verification`

Resend the confirmation email. Always `200` (no account enumeration).

**Request** → `{ "email": "conor@example.com" }`

**Response `200 OK`** → `{ "message": "If an account exists and is unverified, a new verification link has been sent." }`

---

### POST `/api/auth/login`

**Request**
```json
{ "username": "conor", "password": "hunter2pw" }
```

**Response `200 OK`**
```json
{
  "token": "Hh3k...base64url",
  "user": { "id": 1, "username": "conor", "email": "conor@example.com", "available_coins": 100000, "reserved_coins": 0, "dark_mode": false }
}
```

| Code | When |
|------|------|
| `200` | Authenticated, session issued |
| `400` | `username is required` / `password is required` |
| `401` | `invalid credentials` |
| `403` | `Please verify your email before logging in.` |

---

### POST `/api/auth/forgot-password`

Begin a password reset. Always `200` (no account enumeration); if the email
matches an account, a reset link is sent.

**Request** → `{ "email": "conor@example.com" }`

**Response `200 OK`** → `{ "message": "If an account exists for that email, a password reset link has been sent." }`

---

### POST `/api/auth/reset-password`

Finish a password reset. On success **every session for the user is revoked**.

**Request**
```json
{ "token": "<token from the reset email>", "newPassword": "my-new-pw" }
```

**Response `200 OK`** → `{ "ok": true }`

| Code | When |
|------|------|
| `200` | Password changed; sessions revoked |
| `400` | `invalid or expired token`, or password too short |

---

### GET `/api/auth/me`

Current user. **Auth required.**

**Response `200 OK`**
```json
{ "id": 1, "username": "conor", "email": "conor@example.com", "available_coins": 98500, "reserved_coins": 1500, "dark_mode": false }
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
| `status` | string | One of `UNLISTED`, `ACTIVE`, `DELISTING_PENDING`, `LIQUIDATED`. Omit for all. |

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

## Profiles — `/api/users` (public, no auth)

### GET `/api/users/{username}/profile`

A user's public profile: holdings marked to market, realized/unrealized P&L, the
executed trade tape, and the full order history. `realizedPnl` is computed by
replaying the user's trades with average-cost accounting (the same basis
holdings use). All amounts are in sub-units.

**Response `200 OK`**
```json
{
  "username": "conor",
  "realizedPnl": 1200,
  "unrealizedPnl": -300,
  "holdingsValue": 4500,
  "nextPayoutAt": "2026-06-15T06:00:00Z",
  "payoutAmount": 50000,
  "holdings": [
    {
      "fighterId": 1,
      "fighterName": "Jon Jones",
      "photo": "https://...",
      "quantity": 30,
      "reservedQuantity": 0,
      "averagePrice": 140,
      "lastPrice": 150,
      "marketValue": 4500,
      "unrealizedPnl": 300
    }
  ],
  "trades": [
    {
      "fighterId": 1,
      "fighterName": "Jon Jones",
      "side": "BUY",
      "price": 140,
      "quantity": 30,
      "executedAt": "2026-06-07T12:34:56.789Z"
    }
  ],
  "orders": [
    {
      "id": 42,
      "fighterId": 1,
      "fighterName": "Jon Jones",
      "side": "SELL",
      "type": "LIMIT",
      "limitPrice": 160,
      "quantity": 10,
      "filledQuantity": 0,
      "status": "CANCELLED",
      "createdAt": "2026-06-07T12:40:00.000Z"
    }
  ]
}
```

| Field | Meaning |
|-------|---------|
| `holdings` | Open positions (quantity > 0), each an `/api/portfolio` position object |
| `trades` | Executed fills from the user's perspective, **newest first**, capped at 100 |
| `orders` | The user's orders across **every** status (`OPEN`, `PARTIAL`, `FILLED`, `CANCELLED`) — so pending and cancelled buys and sells are included — newest first, capped at 100. Same shape as an `OrderDto`. |
| `nextPayoutAt` | When the next recurring salary is paid to **all** users (ISO-8601 UTC), computed from the payout schedule. `null` if no schedule is configured. |
| `payoutAmount` | Salary paid each payout, in sub-units (e.g. `50000` = 500 coins). |

Profiles are public, so the `orders` list exposes a user's open and cancelled
orders. The internal treasury account has no public profile.

| Code | When |
|------|------|
| `200` | OK |
| `404` | `user not found` (unknown username, or the treasury account) |

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

## Preferences — `/api/preferences` (auth required)

The current user's UI preferences. Currently just dark mode. The client also
caches this locally (a `theme` cookie + `localStorage`) so the choice applies
before first paint; this endpoint is the cross-device source of truth.

### GET `/api/preferences`

**Response `200 OK`** → `{ "darkMode": false }`

| Code | When |
|------|------|
| `200` | OK |
| `401` | Not authenticated |

### PUT `/api/preferences`

**Request** → `{ "darkMode": true }`

**Response `200 OK`** → the saved preferences, e.g. `{ "darkMode": true }`

| Code | When |
|------|------|
| `200` | Saved |
| `401` | Not authenticated |

---

## Admin — `/api/admin`

Not user-authenticated. The caller must present a shared secret in the
**`X-Admin-Api-Key`** header matching the server's `ADMIN_API_KEY`. The
comparison is constant-time and **fails closed** — if no key is configured on the
server, every admin request is rejected with `503`.

### POST `/api/admin/coins`

Credit a user with coins. On success it also pushes a live `BALANCE_UPDATE` over
the WebSocket (see below) to that user's open tabs.

**Headers:** `X-Admin-Api-Key: <secret>`

**Request**
```json
{ "username": "conor", "amount": 500 }
```

| Field | Type | Notes |
|-------|------|-------|
| `username` | string | Recipient; must exist |
| `amount` | integer | Whole coins (positive). Stored as `amount × 100` sub-units. |

**Response `200 OK`** — empty body.

| Code | When |
|------|------|
| `200` | Coins credited |
| `400` | Validation failure (blank username / non-positive amount) |
| `401` | Missing/invalid `X-Admin-Api-Key` |
| `404` | `User not found` |
| `503` | `admin api key not configured` (server has no `ADMIN_API_KEY`) |

---

## Live updates — WebSocket `/ws`

Not under `/api`, so it is **not** rate limited. Connect to `ws://<host>:8080/ws`.

**Auth:** the handshake is authenticated by the httpOnly `session` cookie, sent
automatically by the browser (a `?token=<sessionToken>` query param is also
accepted for non-browser clients). Sockets that don't resolve to a logged-in
user are closed immediately with `1008` (policy violation).

**Messages (server → client):** JSON text frames. Switch on `type` and ignore
unknown types. All amounts are in sub-units.

```json
{ "type": "BALANCE_UPDATE", "availableCoins": 99000, "reservedCoins": 1500 }
```
Sent to a specific user when their balance changes — an admin coin grant, or
after they place/cancel an order. Matches the numbers from `/api/auth/me`.

```json
{ "type": "MARKET_UPDATE", "fighterId": 1, "lastPrice": 100 }
```
Broadcast to all connected clients when a fighter's order book or price changes
(any order placed, cancelled, or filled). Clients viewing that fighter should
refetch the book/trades. All events fire **after** the DB transaction commits.

---

## Quick reference

| Method | Path | Auth | Purpose |
|--------|------|:----:|---------|
| POST | `/api/auth/signup` | – | Create account (sends confirmation email) |
| POST | `/api/auth/verify` | – | Confirm email |
| POST | `/api/auth/resend-verification` | – | Resend confirmation email |
| POST | `/api/auth/login` | – | Log in (requires verified email) |
| POST | `/api/auth/forgot-password` | – | Request a password reset email |
| POST | `/api/auth/reset-password` | – | Set a new password from reset token |
| GET | `/api/auth/me` | ✓ | Current user |
| POST | `/api/auth/logout` | ✓ | End session |
| GET | `/api/fighters` | – | List fighters |
| GET | `/api/fighters/{id}` | – | Fighter detail |
| GET | `/api/fighters/{id}/orderbook` | – | Aggregated book |
| GET | `/api/fighters/{id}/trades` | – | Recent trades |
| GET | `/api/users/{username}/profile` | – | Public profile (holdings, P&L, trades, orders) |
| POST | `/api/orders` | ✓ | Place buy/sell order |
| DELETE | `/api/orders/{id}` | ✓ | Cancel order |
| GET | `/api/orders` | ✓ | List your orders |
| GET | `/api/orders/{id}` | ✓ | One order |
| GET | `/api/portfolio` | ✓ | Holdings + P&L |
| GET | `/api/portfolio/summary` | ✓ | Net worth |
| GET | `/api/wallet` | ✓ | Coin balances |
| GET | `/api/preferences` | ✓ | Read UI preferences (dark mode) |
| PUT | `/api/preferences` | ✓ | Update UI preferences (dark mode) |
| POST | `/api/admin/coins` | API key | Credit a user with coins |
| WS | `/ws` | cookie | Live balance updates |

## Example: a full trade flow

```bash
# 1. sign up. This sends a confirmation email and does NOT return a token —
#    the account must confirm its email before it can log in.
curl -s -X POST localhost:8080/api/auth/signup \
  -H 'Content-Type: application/json' \
  -d '{"email":"conor@example.com","username":"conor","password":"hunter2pw"}'

# 2. confirm the email using the token from the link, then log in for a token
curl -s -X POST localhost:8080/api/auth/verify \
  -H 'Content-Type: application/json' \
  -d '{"token":"<token from the confirmation email>"}'

TOKEN=$(curl -s -X POST localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"conor","password":"hunter2pw"}' | jq -r .token)

# 3. browse the market
curl -s localhost:8080/api/fighters

# 4. place a limit buy for 10 shares of fighter 1 at 1.50 coins
curl -s -X POST localhost:8080/api/orders \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"fighterId":"1","side":"BUY","type":"LIMIT","limitPrice":150,"quantity":10}'

# 5. check your positions, net worth, and public profile
curl -s localhost:8080/api/portfolio        -H "Authorization: Bearer $TOKEN"
curl -s localhost:8080/api/portfolio/summary -H "Authorization: Bearer $TOKEN"
curl -s localhost:8080/api/users/conor/profile
```
