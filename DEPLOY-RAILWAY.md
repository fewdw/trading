# Deploying to Railway

This stack is five pieces that Docker Compose runs together locally. Railway runs
each as its own **service** inside one **project**, joined by a private network.

| Local compose service | Railway service | Public URL? | Notes |
|---|---|---|---|
| `postgres` | **Postgres** (plugin) | no | Managed database, private only |
| `backend`  | **backend**  | **yes** | Spring Boot; public so the browser can open the WebSocket |
| `scrape`   | **scrape**   | no | Python scraper; only the backend calls it |
| `frontend` | **frontend** | **yes** | Next.js; what users visit |
| `agents`   | **agents**   | no | Python AI trading bots; only call the backend API |

> Railway reference syntax `${{Service.VAR}}` pulls a value from another service.
> It only resolves **after** the referenced service exists and (for domains) has a
> domain generated — so create services and generate domains *first*, then set the
> variables below. Saving a variable triggers a redeploy automatically.

---

## 1. Create the project and services

1. Push this repo to GitHub (it already lives at `github.com/fewdw/trading`).
2. Railway → **New Project → Deploy from GitHub repo** → pick the repo.
3. That creates one service. Add the other three with **New → GitHub Repo** (same repo).
4. For **each** of the four services, open **Settings → Source** and set the
   **Root Directory** so Railway uses that folder's Dockerfile:
   - backend  → `server`
   - scrape   → `scrape`
   - frontend → `client`
   - agents   → `agents`
   Rename the services to `backend`, `scrape`, `frontend`, `agents`
   (Settings → Service name) so the variable references below match.
5. **New → Database → Add PostgreSQL.** Leave its name as `Postgres`.

## 2. Generate public domains

- **backend** → Settings → Networking → **Generate Domain** (port `8080`).
- **frontend** → Settings → Networking → **Generate Domain** (port `3000`).
- Do **not** give `scrape` or `Postgres` a public domain.

## 3. Set variables

Open each service's **Variables** tab and add these (Raw Editor makes it fast).

### backend
```
PORT=8080
SPRING_DATASOURCE_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
SPRING_DATASOURCE_USERNAME=${{Postgres.PGUSER}}
SPRING_DATASOURCE_PASSWORD=${{Postgres.PGPASSWORD}}
SCRAPER_URL=http://${{scrape.RAILWAY_PRIVATE_DOMAIN}}:5001
ADMIN_API_KEY=<paste a strong random string: `openssl rand -hex 32`>
WS_ALLOWED_ORIGINS=https://${{frontend.RAILWAY_PUBLIC_DOMAIN}}
```
> `WS_ALLOWED_ORIGINS` locks the `/ws` WebSocket to the frontend's origin so a
> stray site can't open sockets against the backend. Leave it unset (or `*`) only
> for local testing. Actuator (metrics/prometheus) runs on its own port and is
> **not** part of the generated public domain, so it stays private automatically —
> no extra config needed.

Optional backend tuning (set only to override the defaults):
```
FIGHTER_SEED_PRICE=1250          # IPO price per share in sub-units (1250 = 12.50 coins)
FIGHTER_SEED_SHARES=1000         # shares minted + listed per fighter at IPO
PORTFOLIO_SNAPSHOTS_PER_HOUR=1   # 1 = hourly, 2 = every 30 min, 4 = every 15 min
SALARY_AMOUNT=50000              # salary per payout in sub-units (50000 = 500 coins)
SALARY_INTERVAL_DAYS=14          # 1 = daily, 2 = every other day, 14 = every two weeks
```
> Seed price/shares apply to fighters listed *after* the change, so for a clean
> slate set them before first boot (or against an empty DB). Salary is always
> paid at 06:00 UTC; the profile's "next payout" line reflects the interval.

### frontend
```
PORT=3000
BACKEND_URL=https://${{backend.RAILWAY_PUBLIC_DOMAIN}}
BACKEND_WS_URL=wss://${{backend.RAILWAY_PUBLIC_DOMAIN}}/ws
SITE_URL=https://${{frontend.RAILWAY_PUBLIC_DOMAIN}}
NODE_ENV=production
```

> `PORT=3000` matters: Railway injects its own `PORT` at runtime (overriding the
> Dockerfile's `ENV PORT=3000`), so without pinning it Next listens on a port the
> public domain isn't routing to — you get "Application failed to respond". The
> domain's target port (Settings → Networking) must also be `3000`.

### scrape
No variables needed.

### agents
```
RUN_WITH_AI=1
BACKEND_URL=http://${{backend.RAILWAY_PRIVATE_DOMAIN}}:8080
ADMIN_API_KEY=${{backend.ADMIN_API_KEY}}
GEMINI_API_KEY=<your Google AI Studio key>
GEMINI_MODEL=gemini-2.5-flash
```

> `RUN_WITH_AI=0` turns the agents off entirely (the service starts, logs, and
> idles — no trading); `1` runs them. A quick kill-switch without deleting the
> service.

> The agents talk only to the backend, over the **private** network — no public
> domain and no egress fee, even though they make a lot of small requests.
> `ADMIN_API_KEY` references the backend's key so the two never drift; the agents
> use it to provision their bot accounts via `POST /api/admin/agents` (this also
> means the backend must have `ADMIN_API_KEY` set — it already does). If the
> agents' logs show connection errors reaching the backend over the private
> domain, switch `BACKEND_URL` to the backend's **public** domain
> (`https://${{backend.RAILWAY_PUBLIC_DOMAIN}}`) as a fallback. Leave
> `GEMINI_API_KEY` blank to run the bots on their deterministic fallback (no LLM).

> **Tuning (optional):** `AGENT_TICK_SECONDS` (default 30), `STRATEGY_REFRESH_SECONDS`
> (default 240), and `MAX_FIGHTERS` (default 20) trade off market liveliness
> against Gemini spend.

## 4. Deploy

Saving the variables redeploys everything. Watch each service's **Deploy logs**.
Order things settle in: Postgres → scrape → backend → frontend → agents.

Visit the frontend's public domain. Then check:
- Pages load (frontend → backend over HTTPS works).
- Live prices tick and the coin badge updates (browser WebSocket → backend `/ws`).
- Sign up → you're logged in immediately (auth is username + password only).
- The **agents** logs show ~10 bots provisioned and placing orders; within a
  minute the leaderboard shows 🤖 agents and the fighter order books fill in.

---

## Why these values

- **`PORT=8080` on backend** — the app now reads `${PORT}` (`application.properties`).
  Pinning it to 8080 keeps the port deterministic so nothing has to guess it.
- **`BACKEND_URL` is the *public* backend URL.** The Next server fetches the backend
  server-side; using the public URL avoids Railway's IPv6 private-DNS quirk in Node.
  Railway shows an **egress-fee warning** on this variable — that's expected and the
  cost is negligible for a hobby app. Optional optimization once it's working: switch
  to private + no egress with `BACKEND_URL=http://${{backend.RAILWAY_PRIVATE_DOMAIN}}:8080`
  (if Node then can't reach it, add `NODE_OPTIONS=--dns-result-order=ipv6first` to the
  frontend).
- **`BACKEND_WS_URL`** — the browser opens the live socket straight to the backend.
  `/api/ws-token` reads the httpOnly `session` cookie server-side and hands the
  browser this URL + the token, which the socket sends as `?token=` (the backend
  already accepts that). This replaces the old hardcoded `:8080` same-host URL.
- **`SCRAPER_URL` / datasource use the *private* network** — backend→scrape and
  backend→Postgres are Java (IPv6-clean) and never need to be public.

## Gotchas

- **Postgres SSL:** the private connection shouldn't need SSL. If the backend logs
  an SSL error, append `?sslmode=disable` (private) or `?sslmode=require` to
  `SPRING_DATASOURCE_URL`.
- **Private networking has a few-seconds startup delay**; `restartPolicyType` in each
  `railway.json` handles the early flaps.
- **Schema:** `spring.jpa.hibernate.ddl-auto=update` creates tables on first boot.

## Cleaner auth later (optional)

Today the browser fetches its session token to auth the WebSocket, which makes the
token readable by your own JS. If you point a custom domain at this (e.g.
`app.example.com` + `api.example.com`), we can set the `session` cookie with
`Domain=.example.com; SameSite=None; Secure` so it rides the WS handshake again and
stays fully httpOnly. The current setup keeps working either way.
