"""The deterministic half of the hybrid design.

Turns an LLM ``StrategyDirective`` into concrete order-book actions, enforcing
hard risk limits the LLM cannot override:

* never spend more coins than the wallet holds (tracked across fighters in-tick),
* never sell more than free (unreserved) shares — no shorting,
* cap the position per fighter,
* clamp every quote into a sane band around the last price (never <= 0),
* bound how many orders an agent works at once.

Execution archetype is chosen by ``persona.style``:
  maker        -> always quote a bid and an ask around mid (provide liquidity)
  directional  -> cross the spread in the stance's direction (take liquidity)
  value        -> rest a patient order on the stance's side
"""

from __future__ import annotations

import logging
import random
from dataclasses import dataclass
from datetime import datetime, timezone

from api import MarketClient
from personas import Persona
from strategist import StrategyDirective, Stance

log = logging.getLogger("agents.executor")

HARD_MAX_POSITION = 400  # absolute ceiling regardless of the directive


@dataclass(frozen=True)
class ExecConfig:
    base_qty: int = 14
    order_ttl_seconds: float = 45.0
    max_open_orders: int = 14
    max_placements_per_tick: int = 6
    # Global throttle/throttle-up. <1 = fewer, smaller, slower trades; >1 = more
    # orders per tick, bigger size, and harder price pushes (see _intents). Comes
    # from the TRADE_RATE env var.
    trade_rate: float = 1.5

    @property
    def placements_per_tick(self) -> int:
        """How many orders one agent may place per tick (scaled by TRADE_RATE)."""
        return max(1, round(self.max_placements_per_tick * self.trade_rate))

    @property
    def open_orders_cap(self) -> int:
        """How many resting orders one agent may work at once (scaled likewise)."""
        return max(2, round(self.max_open_orders * self.trade_rate))


def run_tick(
    persona: Persona,
    directive: StrategyDirective,
    market: dict[int, dict],
    client: MarketClient,
    token: str,
    cfg: ExecConfig,
) -> None:
    """One execution cycle for one agent. Caller handles Unauthorized."""
    _prune_orders(client, token, cfg)

    live = client.open_orders(token)
    live_sides = {(o["fighterId"], o["side"]) for o in live}

    wallet = client.wallet(token)
    avail = int(wallet["availableCoins"])
    positions = {p["fighterId"]: p for p in client.portfolio(token)}

    focus = _focus_fighters(directive, market, cfg)
    placed = 0
    for fid in focus:
        if placed >= cfg.placements_per_tick:
            break
        if len(live_sides) >= cfg.open_orders_cap:
            break
        entry = market.get(fid)
        if not entry:
            continue
        fighter, ob = entry["fighter"], entry["orderbook"]
        if fighter.get("status") != "ACTIVE":
            continue

        position = positions.get(fid)
        for side, qty, price in _intents(persona, directive, fighter, ob, position, cfg):
            if placed >= cfg.placements_per_tick:
                break
            if (fid, side) in live_sides:
                continue  # already working this side
            qty, price = _apply_limits(
                side, qty, price, fighter, avail, position
            )
            if qty <= 0:
                continue
            res = client.place_order(token, fid, side, qty, price)
            if res is None:
                continue
            placed += 1
            live_sides.add((fid, side))
            if side == "BUY":
                avail -= qty * price  # reserve locally so we don't overspend
            log.info(
                "%-15s %-4s %s x%s @%s  [%s a=%.2f] %s",
                persona.username, side, fighter["name"], qty, price,
                directive.stance.value, directive.aggressiveness,
                directive.reasoning,
            )


# --- intent generation (per style) ----------------------------------------

def _intents(persona, directive, fighter, ob, position, cfg) -> list[tuple[str, int, int]]:
    """Decide this fighter's orders. A lively market comes from *crossing*: an
    aggressive buy lifts the offer and walks it up; an aggressive sell hits the
    bid and walks it down. The walk distance and order size both scale with the
    persona's innate aggression, the LLM's aggressiveness this cycle, and the
    global TRADE_RATE — so price impact and spend grow together and the chart
    actually moves. An inventory target turns every agent into a two-way trader
    (buy up to the target, then sell back down), so resting orders get filled."""
    last = int(fighter.get("lastPrice") or 1250)
    bb, ba = _best_bid(ob), _best_ask(ob)
    mid = _mid(ob, last)

    # How hard this agent pushes this tick. Blends persona temperament, the LLM's
    # per-cycle aggressiveness, and the fleet-wide TRADE_RATE.
    intensity = (
        persona.aggression
        * (0.45 + 0.85 * directive.aggressiveness)
        * cfg.trade_rate
    )
    intensity = max(0.15, min(4.0, intensity))

    # Walk distance past the touch, in sub-units: ~4% of price at intensity 1,
    # reaching well past several resting levels on high-conviction names so a
    # single order sweeps the book. The floor of 2 keeps moves visible on
    # low-priced fighters, where a percentage tick would round to one sub-unit
    # (the old behaviour that pinned everything to 1.00 / 0.99 / 1.01).
    walk = max(2, int(last * 0.04 * intensity))
    spread = max(1, int(mid * (0.01 + 0.03 * directive.aggressiveness)))
    qty = max(1, int(cfg.base_qty * persona.size * (0.4 + intensity)))

    held = int(position["quantity"]) if position else 0
    reserved = int(position["reservedQuantity"]) if position else 0
    free = max(0, held - reserved)
    target = max(1, min(HARD_MAX_POSITION, directive.max_position_per_fighter or 60))

    # Reference prices. "take" crosses the spread and walks the book (fills now);
    # "rest" posts a passive order that improves the touch.
    buy_take = (ba + walk) if ba else int(mid * (1.0 + 0.05 * intensity))  # lift & sweep up
    buy_rest = (bb + 1) if bb else (mid - spread)                         # improve the bid
    sell_take = (bb - walk) if bb else None                              # hit & sweep down
    sell_rest = (ba - 1) if ba else (mid + spread)                       # undercut the offer

    stance, style = directive.stance, persona.style

    # Market makers are the liquidity layer: always two-sided around the mid, in
    # real size, on every name they touch, with only a light directional skew.
    # They supply the depth the takers below sweep through.
    if style == "maker":
        intents: list[tuple[str, int, int]] = []
        buy_q = qty if stance != Stance.BEARISH else max(1, qty // 2)
        intents.append(("BUY", buy_q, buy_rest))
        if free > 0:
            sell_q = qty if stance != Stance.BULLISH else max(1, qty // 2)
            intents.append(("SELL", min(sell_q, free), sell_rest))
        return intents

    intents = []

    # BUY: accumulate toward the target. Directional styles (and anyone outright
    # bullish) cross the spread and sweep the offers; a value agent rests a
    # patient bid unless it's bullish. Bearish agents buy only when underweight.
    if held < target and (stance != Stance.BEARISH or held < target // 2):
        cross = style == "directional" or stance == Stance.BULLISH
        intents.append(("BUY", qty, buy_take if cross else buy_rest))

    # SELL: only shares we own. Cross *down* into the bids when bearish or over
    # the target (this fills resting bids and drives price down); otherwise
    # undercut the offer patiently.
    if free > 0 and (stance != Stance.BULLISH or held > target):
        cross = (stance == Stance.BEARISH or held > target) and sell_take is not None
        price = sell_take if cross else sell_rest
        intents.append(("SELL", min(qty, free), price))

    return intents


# --- risk limits -----------------------------------------------------------

def _apply_limits(side, qty, price, fighter, avail, position) -> tuple[int, int]:
    last = int(fighter.get("lastPrice") or 1250)
    price = _clamp_price(price, last)
    if price <= 0 or qty <= 0:
        return 0, price

    if side == "BUY":
        held = int(position["quantity"]) if position else 0
        room = max(0, HARD_MAX_POSITION - held)
        qty = min(qty, room, avail // price)
    else:  # SELL — only what we own free (no shorting)
        free = (
            int(position["quantity"]) - int(position["reservedQuantity"])
            if position else 0
        )
        qty = min(qty, max(0, free))
    return max(0, qty), price


def _prune_orders(client: MarketClient, token: str, cfg: ExecConfig) -> None:
    """Cancel orders older than the TTL, and the oldest extras over the cap, so
    quotes stay fresh and reservations get released."""
    orders = client.open_orders(token)
    now = datetime.now(timezone.utc).timestamp()
    by_age = sorted(orders, key=lambda o: o["createdAt"])  # oldest first
    keep: list[dict] = []
    for o in by_age:
        age = now - _ts(o["createdAt"])
        if age > cfg.order_ttl_seconds:
            client.cancel_order(token, o["id"])
        else:
            keep.append(o)
    # Still over the cap? Cancel the oldest survivors.
    while len(keep) > cfg.open_orders_cap:
        client.cancel_order(token, keep.pop(0)["id"])


# --- small helpers ---------------------------------------------------------

def _focus_fighters(
    directive: StrategyDirective, market: dict[int, dict], cfg: ExecConfig
) -> list[int]:
    ids = [i for i in directive.focus_fighter_ids if i in market]
    # Touch more names per tick as TRADE_RATE rises (and never zero). Keep every
    # LLM-chosen name, then top up with random extras to reach the target breadth.
    want = min(len(market), max(2, round(2 * cfg.trade_rate)))
    if len(ids) < want:
        pool = [i for i in market if i not in ids]
        random.shuffle(pool)
        ids = ids + pool[: want - len(ids)]
    return ids


def _best_bid(ob) -> int | None:
    bids = ob.get("bids") or []
    return int(bids[0]["price"]) if bids else None


def _best_ask(ob) -> int | None:
    asks = ob.get("asks") or []
    return int(asks[0]["price"]) if asks else None


def _mid(ob, last: int) -> int:
    bb, ba = _best_bid(ob), _best_ask(ob)
    if bb and ba:
        return (bb + ba) // 2
    if bb:
        return bb
    if ba:
        return ba
    return last


def _clamp_price(price: int, last: int) -> int:
    lo = max(1, int(last * 0.5))
    hi = max(lo + 1, int(last * 1.5))
    return max(lo, min(hi, int(price)))


def _ts(iso: str) -> float:
    # Backend sends ISO-8601 UTC, sometimes with a trailing Z.
    return datetime.fromisoformat(iso.replace("Z", "+00:00")).timestamp()
