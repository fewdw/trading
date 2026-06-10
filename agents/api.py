"""Thin REST client for the Fighter Market backend.

Agents talk to the *public* API exactly like a human player would — the only
privileged call is ``provision_agent`` (admin-key gated), which creates the bot
account and returns a session token. Everything else uses that bearer token.
"""

from __future__ import annotations

import logging
from typing import Any, Optional

import httpx

log = logging.getLogger("agents.api")


class Unauthorized(Exception):
    """Raised on a 401 so the caller can re-provision a fresh token."""


class MarketClient:
    def __init__(self, base_url: str, admin_api_key: str, timeout: float = 10.0):
        self._base = base_url.rstrip("/")
        self._admin_api_key = admin_api_key
        self._http = httpx.Client(base_url=self._base, timeout=timeout)

    def close(self) -> None:
        self._http.close()

    # --- provisioning (admin) ---------------------------------------------

    def provision_agent(self, username: str) -> str:
        """Find-or-create the agent account; returns a 7-day session token."""
        res = self._http.post(
            "/api/admin/agents",
            headers={"X-Admin-Api-Key": self._admin_api_key},
            json={"username": username},
        )
        res.raise_for_status()
        return res.json()["token"]

    # --- market data (public) ---------------------------------------------

    def active_fighters(self) -> list[dict[str, Any]]:
        res = self._http.get("/api/fighters", params={"status": "ACTIVE"})
        res.raise_for_status()
        return res.json()

    def orderbook(self, fighter_id: int) -> dict[str, Any]:
        res = self._http.get(f"/api/fighters/{fighter_id}/orderbook")
        res.raise_for_status()
        return res.json()

    def recent_trades(self, fighter_id: int) -> list[dict[str, Any]]:
        res = self._http.get(f"/api/fighters/{fighter_id}/trades")
        res.raise_for_status()
        return res.json()

    # --- account (bearer token) -------------------------------------------

    def wallet(self, token: str) -> dict[str, Any]:
        return self._authed_get("/api/wallet", token)

    def portfolio(self, token: str) -> list[dict[str, Any]]:
        return self._authed_get("/api/portfolio", token)

    def open_orders(self, token: str) -> list[dict[str, Any]]:
        return self._authed_get("/api/orders", token, params={"status": "open"})

    def cancel_order(self, token: str, order_id: int) -> None:
        res = self._http.delete(f"/api/orders/{order_id}", headers=_bearer(token))
        if res.status_code == 401:
            raise Unauthorized()
        # 404/409 just mean it already filled/cancelled — ignore.

    def place_order(
        self,
        token: str,
        fighter_id: int,
        side: str,
        quantity: int,
        limit_price: int,
    ) -> Optional[dict[str, Any]]:
        """Place a LIMIT order. Returns the OrderDto, or None on a rejection
        the engine expects (insufficient funds/shares, not-tradable, conflict)."""
        body = {
            "fighterId": str(fighter_id),
            "side": side,
            "type": "LIMIT",
            "limitPrice": limit_price,
            "quantity": quantity,
        }
        res = self._http.post("/api/orders", headers=_bearer(token), json=body)
        if res.status_code == 401:
            raise Unauthorized()
        if res.status_code in (200, 201):
            return res.json()
        # 400/404/409 are normal trading rejections; log at debug and move on.
        log.debug(
            "order rejected (%s) %s %s x%s @%s: %s",
            res.status_code,
            side,
            fighter_id,
            quantity,
            limit_price,
            res.text,
        )
        return None

    # --- internals ---------------------------------------------------------

    def _authed_get(self, path: str, token: str, params: Optional[dict] = None) -> Any:
        res = self._http.get(path, headers=_bearer(token), params=params)
        if res.status_code == 401:
            raise Unauthorized()
        res.raise_for_status()
        return res.json()


def _bearer(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}
