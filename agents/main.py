"""AI trading agents service for Fighter Market.

A long-running worker that runs ~10 autonomous agents. Each agent is a real bot
account that trades through the public matching engine. Design is *hybrid*:

  * Gemini sets each agent's high-level strategy every few minutes (staggered,
    so LLM cost stays low), and
  * a deterministic executor turns that strategy into order-book actions every
    tick (~30s), inside hard risk limits.

The whole thing is additive: it only talks to the backend's HTTP API.
"""

from __future__ import annotations

import logging
import os
import random
import time
from dataclasses import dataclass, field

import httpx

from api import MarketClient, Unauthorized
from executor import ExecConfig, run_tick
from personas import ROSTER, Persona
from strategist import StrategyDirective, Stance, Strategist

log = logging.getLogger("agents")


def _env(name: str, default: str | None = None, required: bool = False) -> str:
    val = os.environ.get(name, default)
    if required and not val:
        raise SystemExit(f"missing required env var: {name}")
    return val or ""


@dataclass
class Config:
    backend_url: str
    admin_api_key: str
    gemini_api_key: str
    gemini_model: str
    tick_seconds: float
    refresh_seconds: float
    max_fighters: int
    exec_cfg: ExecConfig

    @staticmethod
    def from_env() -> "Config":
        return Config(
            backend_url=_env("BACKEND_URL", "http://localhost:8080"),
            admin_api_key=_env("ADMIN_API_KEY", required=True),
            gemini_api_key=_env("GEMINI_API_KEY"),
            gemini_model=_env("GEMINI_MODEL", "gemini-2.5-flash"),
            tick_seconds=float(_env("AGENT_TICK_SECONDS", "30")),
            refresh_seconds=float(_env("STRATEGY_REFRESH_SECONDS", "240")),
            max_fighters=int(_env("MAX_FIGHTERS", "20")),
            exec_cfg=ExecConfig(
                base_qty=int(_env("BASE_QTY", "8")),
                order_ttl_seconds=float(_env("ORDER_TTL_SECONDS", "60")),
            ),
        )


@dataclass
class Agent:
    persona: Persona
    token: str
    next_refresh: float
    directive: StrategyDirective = field(
        default_factory=lambda: StrategyDirective(
            stance=Stance.NEUTRAL, reasoning="warming up"
        )
    )


def provision_all(client: MarketClient, refresh_seconds: float) -> list[Agent]:
    """Provision every persona, retrying until the backend is reachable. Stagger
    each agent's first LLM refresh across the refresh window to avoid a burst."""
    agents: list[Agent] = []
    n = len(ROSTER)
    start = time.time()
    for i, persona in enumerate(ROSTER):
        while True:
            try:
                token = client.provision_agent(persona.username)
                break
            except (httpx.HTTPError, Exception) as e:  # backend may still be booting
                log.warning("provision %s failed (%s); retrying in 5s", persona.username, e)
                time.sleep(5)
        agents.append(
            Agent(
                persona=persona,
                token=token,
                next_refresh=start + (i / n) * refresh_seconds,
            )
        )
        log.info("provisioned %s", persona.username)
    return agents


def build_market(client: MarketClient, max_fighters: int) -> dict[int, dict]:
    """Snapshot the tradable universe once per tick (shared by all agents)."""
    try:
        fighters = client.active_fighters()[:max_fighters]
    except Exception:
        log.exception("could not list fighters; skipping tick")
        return {}
    market: dict[int, dict] = {}
    for f in fighters:
        try:
            ob = client.orderbook(f["id"])
        except Exception:
            ob = {"bids": [], "asks": []}
        market[f["id"]] = {"fighter": f, "orderbook": ob}
    return market


def llm_snapshot(market: dict[int, dict]) -> dict:
    fighters = []
    for fid, e in market.items():
        f, ob = e["fighter"], e["orderbook"]
        fighters.append(
            {
                "id": fid,
                "name": f["name"],
                "lastPrice": f.get("lastPrice"),
                "bestBid": ob["bids"][0]["price"] if ob.get("bids") else None,
                "bestAsk": ob["asks"][0]["price"] if ob.get("asks") else None,
            }
        )
    return {"fighters": fighters}


def account_summary(client: MarketClient, token: str) -> dict:
    wallet = client.wallet(token)
    holdings = client.portfolio(token)
    return {
        "availableCoins": wallet["availableCoins"],
        "reservedCoins": wallet["reservedCoins"],
        "holdings": [
            {
                "fighterId": h["fighterId"],
                "qty": h["quantity"],
                "avgPrice": h["averagePrice"],
            }
            for h in holdings
        ],
    }


def _ai_enabled() -> bool:
    return os.environ.get("RUN_WITH_AI", "1").strip().lower() in (
        "1", "true", "yes", "on"
    )


def main() -> None:
    logging.basicConfig(
        level=os.environ.get("LOG_LEVEL", "INFO"),
        format="%(asctime)s %(levelname)-5s %(name)s: %(message)s",
    )

    if not _ai_enabled():
        # RUN_WITH_AI=0: do not provision or trade anything. Stay alive and idle
        # so the container doesn't restart-loop under a restart policy.
        log.warning("RUN_WITH_AI is off — AI trading disabled; idling.")
        try:
            while True:
                time.sleep(3600)
        except KeyboardInterrupt:
            pass
        return

    cfg = Config.from_env()
    log.info("starting agents service against %s", cfg.backend_url)

    client = MarketClient(cfg.backend_url, cfg.admin_api_key)
    strategist = Strategist(cfg.gemini_api_key, cfg.gemini_model)
    agents = provision_all(client, cfg.refresh_seconds)
    log.info("%d agents live; tick=%ss refresh=%ss llm=%s",
             len(agents), cfg.tick_seconds, cfg.refresh_seconds, strategist.llm_enabled)

    try:
        while True:
            tick_start = time.monotonic()
            market = build_market(client, cfg.max_fighters)
            base_snap = llm_snapshot(market) if market else {"fighters": []}

            if market:
                random.shuffle(agents)  # vary who acts first each tick
                for agent in agents:
                    try:
                        now = time.time()
                        if now >= agent.next_refresh:
                            snap = dict(base_snap)
                            snap["yourPortfolio"] = account_summary(client, agent.token)
                            agent.directive = strategist.decide(agent.persona, snap)
                            agent.next_refresh = now + cfg.refresh_seconds * random.uniform(0.85, 1.15)
                        run_tick(agent.persona, agent.directive, market, client, agent.token, cfg.exec_cfg)
                    except Unauthorized:
                        log.warning("re-provisioning %s (token expired)", agent.persona.username)
                        agent.token = client.provision_agent(agent.persona.username)
                    except Exception:
                        log.exception("agent %s tick failed", agent.persona.username)

            elapsed = time.monotonic() - tick_start
            time.sleep(max(1.0, cfg.tick_seconds - elapsed))
    except KeyboardInterrupt:
        log.info("shutting down")
    finally:
        client.close()


if __name__ == "__main__":
    main()
