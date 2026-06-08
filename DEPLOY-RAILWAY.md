# Deploying to Railway

This stack is four pieces that Docker Compose runs together locally. Railway runs
each as its own **service** inside one **project**, joined by a private network.

| Local compose service | Railway service | Public URL? | Notes |
|---|---|---|---|
| `postgres` | **Postgres** (plugin) | no | Managed database, private only |
| `backend`  | **backend**  | **yes** | Spring Boot; public so the browser can open the WebSocket |
| `scrape`   | **scrape**   | no | Python scraper; only the backend calls it |
| `frontend` | **frontend** | **yes** | Next.js; what users visit |

> Railway reference syntax `${{Service.VAR}}` pulls a value from another service.
> It only resolves **after** the referenced service exists and (for domains) has a
> domain generated — so create services and generate domains *first*, then set the
> variables below. Saving a variable triggers a redeploy automatically.

---

## 1. Create the project and services

1. Push this repo to GitHub (it already lives at `github.com/fewdw/trading`).
2. Railway → **New Project → Deploy from GitHub repo** → pick the repo.
3. That creates one service. Add the other two with **New → GitHub Repo** (same repo).
4. For **each** of the three services, open **Settings → Source** and set the
   **Root Directory** so Railway uses that folder's Dockerfile:
   - backend  → `server`
   - scrape   → `scrape`
   - frontend → `client`
   Rename the services to `backend`, `scrape`, `frontend` (Settings → Service name)
   so the variable references below match.
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
FRONTEND_URL=https://${{frontend.RAILWAY_PUBLIC_DOMAIN}}
ADMIN_API_KEY=<paste a strong random string: `openssl rand -hex 32`>
BREVO_SMTP_LOGIN=<your Brevo SMTP login>
BREVO_SMTP_KEY=<your Brevo SMTP key>
MAIL_FROM=<a Brevo-verified sender address>
```

### frontend
```
BACKEND_URL=https://${{backend.RAILWAY_PUBLIC_DOMAIN}}
BACKEND_WS_URL=wss://${{backend.RAILWAY_PUBLIC_DOMAIN}}/ws
SITE_URL=https://${{frontend.RAILWAY_PUBLIC_DOMAIN}}
NODE_ENV=production
```

### scrape
No variables needed.

## 4. Deploy

Saving the variables redeploys everything. Watch each service's **Deploy logs**.
Order things settle in: Postgres → scrape → backend → frontend.

Visit the frontend's public domain. Then check:
- Pages load (frontend → backend over HTTPS works).
- Live prices tick and the coin badge updates (browser WebSocket → backend `/ws`).
- Sign up → confirmation email arrives (Brevo configured + sender verified).

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
- **Email:** `MAIL_FROM` must be a sender you've verified in Brevo, or sends bounce.
- **Schema:** `spring.jpa.hibernate.ddl-auto=update` creates tables on first boot.

## Cleaner auth later (optional)

Today the browser fetches its session token to auth the WebSocket, which makes the
token readable by your own JS. If you point a custom domain at this (e.g.
`app.example.com` + `api.example.com`), we can set the `session` cookie with
`Domain=.example.com; SameSite=None; Secure` so it rides the WS handshake again and
stays fully httpOnly. The current setup keeps working either way.
