# AI Trading Agents

Autonomous trading bots that populate Fighter Market with activity. ~10 agents,
each a real bot account, trade against the **same public matching engine** as
human players. They are clearly marked with a 🤖 on the leaderboard and profiles.

## Design — hybrid LLM + deterministic execution

```
            every ~4 min (staggered)          every ~30s
  ┌────────────────────────────────┐   ┌──────────────────────────────┐
  │ Gemini  →  StrategyDirective    │ → │ deterministic executor        │
  │ (persona + market snapshot)     │   │ (risk limits → order book)    │
  └────────────────────────────────┘   └──────────────────────────────┘
```

* **Gemini sets strategy** (`strategist.py`): given a persona and a compact
  market snapshot, it returns a structured `StrategyDirective`
  (stance / focus fighters / aggressiveness / position cap / reasoning) using
  Gemini's JSON-schema structured-output mode. Called infrequently, so cost is
  low (~2–3 calls/min for the whole fleet on a Flash model).
* **A deterministic executor** (`executor.py`) turns that directive into actual
  LIMIT orders every tick, inside **hard risk limits** it cannot exceed (never
  overspend, never short, position caps, price clamps, bounded open orders).
* **Robust by design:** any LLM failure (no key, network, quota, bad JSON) falls
  back to a safe heuristic directive — the market keeps moving regardless.

The agents are **purely additive**: they only call the backend's HTTP API.

## How an agent is created

Agents don't use public sign-up (which is per-IP throttled). Instead the service
calls the admin-gated `POST /api/admin/agents` once per persona, which
find-or-creates the bot account, sets its `isBot` flag, and returns a session
token. The agent then trades with that bearer token like any user.

## Files

| File | Role |
|------|------|
| `personas.py`   | The ~10 agent personas (username, style, LLM prompt) |
| `strategist.py` | Gemini call → `StrategyDirective` (+ heuristic fallback) |
| `executor.py`   | Directive → concrete orders, with risk limits |
| `api.py`        | Thin Fighter Market REST client |
| `main.py`       | Orchestrator: provision + the tick/refresh loop |

## Configuration (env)

| Var | Default | Notes |
|-----|---------|-------|
| `RUN_WITH_AI` | `1` | `0` disables all AI trading (service idles); `1` runs it |
| `BACKEND_URL` | `http://localhost:8080` | Fighter Market API base |
| `ADMIN_API_KEY` | — (required) | Same key the backend uses; provisions agents |
| `GEMINI_API_KEY` | — | From Google AI Studio. Blank → heuristic fallback |
| `GEMINI_MODEL` | `gemini-2.5-flash` | Use a Flash model for cost |
| `TRADE_RATE` | `1.5` | **The market-liveliness knob.** `<1` = fewer/smaller/slower trades; `>1` = more orders per tick, bigger size, harder price pushes, faster ticks. Clamped to `0.1`–`8.0` |
| `AGENT_TICK_SECONDS` | `30` | Base tick period; the effective period is `AGENT_TICK_SECONDS / TRADE_RATE` |
| `STRATEGY_REFRESH_SECONDS` | `240` | How often Gemini re-strategizes per agent |
| `MAX_FIGHTERS` | `20` | Size of the tradable universe per tick |
| `BASE_QTY` | `14` | Base order size (scaled by persona size, conviction, and `TRADE_RATE`) |
| `ORDER_TTL_SECONDS` | `45` | Resting orders older than this are re-quoted |

### Aggression & personalities

Each persona carries an `aggression` and `size` temperament (`personas.py`) on top
of its LLM prompt, so they behave concretely differently in the executor — e.g.
`breakout_ai` blows through offers to grab size, `marketmaker_ai` quotes both
sides in real size, `value_ai` mostly rests patient orders. Aggressive **takers**
walk the book several price levels per order (lifting offers on the way up,
hitting bids on the way down), which is what actually moves the chart. All of it
scales with `TRADE_RATE`.

## Run

Locally it comes up with the rest of the stack:

```bash
docker compose up --build        # set GEMINI_API_KEY in .env for real LLM behavior
```

Standalone (against a running backend):

```bash
cd agents
pip install -r requirements.txt
BACKEND_URL=http://localhost:8080 ADMIN_API_KEY=... GEMINI_API_KEY=... python main.py
```
