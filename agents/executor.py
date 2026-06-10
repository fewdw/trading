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

HARD_MAX_POSITION = 200  # absolute ceiling regardless of the directive


@dataclass(frozen=True)
class ExecConfig:
    base_qty: int = 8
    order_ttl_seconds: float = 60.0
    max_open_orders: int = 14
    max_placements_per_tick: int = 6


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

    focus = _focus_fighters(directive, market)
    placed = 0
    for fid in focus:
        if placed >= cfg.max_placements_per_tick:
            break
        if len(live_sides) >= cfg.max_open_orders:
            break
        entry = market.get(fid)
        if not entry:
            continue
        fighter, ob = entry["fighter"], entry["orderbook"]
        if fighter.get("status") != "ACTIVE":
            continue

        position = positions.get(fid)
        for side, qty, price in _intents(persona, directive, fighter, ob, position, cfg):
            if placed >= cfg.max_placements_per_tick:
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
    """Decide this fighter's orders. The key to a lively market is *crossing*:
    buys lift the best ask (and walk it up), sells hit the best bid. An inventory
    target turns every agent into a two-way trader — it buys up to the target and
    then sells back down, so resting bids actually get filled."""
    last = int(fighter.get("lastPrice") or 1250)
    bb, ba = _best_bid(ob), _best_ask(ob)
    mid = _mid(ob, last)
    aggr = directive.aggressiveness
    tick = max(1, int(last * 0.005))
    spread = max(1, int(mid * (0.01 + 0.03 * aggr)))
    qty = max(1, int(cfg.base_qty * (0.5 + aggr)))

    held = int(position["quantity"]) if position else 0
    reserved = int(position["reservedQuantity"]) if position else 0
    free = max(0, held - reserved)
    target = max(1, min(HARD_MAX_POSITION, directive.max_position_per_fighter or 50))

    # Reference prices: "take" crosses the spread (fills now); "rest" posts a
    # passive order that improves the book.
    buy_take = (ba + tick) if ba else int(mid * (1.0 + 0.03))  # lift & walk up
    buy_rest = (bb + 1) if bb else (mid - spread)              # improve the bid
    sell_take = bb if bb else None                            # hit the bid
    sell_rest = (ba - 1) if ba else (mid + spread)            # undercut the offer

    stance, style = directive.stance, persona.style
    intents: list[tuple[str, int, int]] = []

    # BUY: accumulate toward the target. Aggressive styles lift the ask; a value
    # agent only rests a patient bid unless it's outright bullish. Bearish agents
    # buy only when badly underweight.
    if held < target and (stance != Stance.BEARISH or held < target // 2):
        patient = style == "value" and stance != Stance.BULLISH
        intents.append(("BUY", qty, buy_rest if patient else buy_take))

    # SELL: only shares we own. Cross down into the bid when bearish or over the
    # target (this is what fills resting bids); otherwise undercut the offer.
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
    while len(keep) > cfg.max_open_orders:
        client.cancel_order(token, keep.pop(0)["id"])


# --- small helpers ---------------------------------------------------------

def _focus_fighters(directive: StrategyDirective, market: dict[int, dict]) -> list[int]:
    ids = [i for i in directive.focus_fighter_ids if i in market]
    if not ids:  # nothing chosen / valid — still do a little something
        ids = random.sample(list(market), min(2, len(market)))
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
