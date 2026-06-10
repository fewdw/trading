"""The LLM half of the hybrid design.

Periodically (not every tick), Gemini is asked to set a *strategy directive* for
one agent given a compact market snapshot and the agent's portfolio. We use
Gemini's structured-output mode (a JSON schema derived from ``StrategyDirective``)
so the response is always machine-readable.

Robustness is deliberate: any failure — missing API key, network error, bad JSON,
quota — falls back to a safe heuristic directive instead of crashing. The
deterministic executor then keeps the market alive regardless of the LLM's state.
"""

from __future__ import annotations

import json
import logging
import random
from enum import Enum

from personas import Persona
from pydantic import BaseModel, Field

log = logging.getLogger("agents.strategist")


class Stance(str, Enum):
    BULLISH = "BULLISH"
    BEARISH = "BEARISH"
    NEUTRAL = "NEUTRAL"


class StrategyDirective(BaseModel):
    """High-level intent for one agent; consumed by the deterministic executor."""

    stance: Stance = Field(description="Overall directional lean for this cycle.")
    focus_fighter_ids: list[int] = Field(
        default_factory=list,
        description="Fighter ids (from the snapshot) to act on this cycle.",
    )
    aggressiveness: float = Field(
        0.5,
        ge=0.0,
        le=1.0,
        description="0 = very passive/small, 1 = very aggressive/large.",
    )
    max_position_per_fighter: int = Field(
        80,
        ge=0,
        description="Soft cap on shares to hold in any one fighter.",
    )
    reasoning: str = Field(
        "", description="One or two sentences explaining the decision."
    )


class Strategist:
    def __init__(self, api_key: str | None, model: str):
        self._model = model
        self._client = None
        if api_key:
            try:
                from google import genai  # imported lazily so dev runs without it

                self._client = genai.Client(api_key=api_key)
                log.info("Gemini strategist enabled (model=%s)", model)
            except Exception:  # pragma: no cover - defensive
                log.exception("failed to init Gemini; using heuristic fallback")
        else:
            log.warning("GEMINI_API_KEY not set — agents run on the heuristic fallback")

    @property
    def llm_enabled(self) -> bool:
        return self._client is not None

    def decide(self, persona: Persona, snapshot: dict) -> StrategyDirective:
        if self._client is None:
            return self._fallback(persona, snapshot)
        try:
            from google.genai import types

            prompt = (
                "Market snapshot and your portfolio (JSON):\n"
                + json.dumps(snapshot, separators=(",", ":"))
                + "\n\nReturn your strategy directive."
            )
            resp = self._client.models.generate_content(
                model=self._model,
                contents=prompt,
                config=types.GenerateContentConfig(
                    system_instruction=persona.system_prompt,
                    response_mime_type="application/json",
                    response_schema=StrategyDirective,
                    temperature=0.9,
                ),
            )
            directive = resp.parsed
            if not isinstance(directive, StrategyDirective):
                # Some SDK versions return text only; parse it ourselves.
                directive = StrategyDirective.model_validate_json(resp.text)
            return self._sanitize(directive, snapshot)
        except Exception:
            log.exception("Gemini call failed for %s; using fallback", persona.username)
            return self._fallback(persona, snapshot)

    # --- safety / fallback -------------------------------------------------

    def _sanitize(self, d: StrategyDirective, snapshot: dict) -> StrategyDirective:
        """Clamp LLM output and drop any fighter ids not in the snapshot."""
        valid_ids = {f["id"] for f in snapshot.get("fighters", [])}
        d.focus_fighter_ids = [i for i in d.focus_fighter_ids if i in valid_ids][:5]
        d.aggressiveness = max(0.0, min(1.0, d.aggressiveness))
        d.max_position_per_fighter = max(0, min(400, d.max_position_per_fighter))
        d.reasoning = (d.reasoning or "")[:300]
        return d

    def _fallback(self, persona: Persona, snapshot: dict) -> StrategyDirective:
        fighters = snapshot.get("fighters", [])
        ids = [f["id"] for f in fighters]
        focus = random.sample(ids, min(3, len(ids))) if ids else []
        if persona.style == "maker":
            stance = Stance.NEUTRAL
        elif persona.style == "value":
            stance = random.choice([Stance.BULLISH, Stance.NEUTRAL, Stance.BEARISH])
        else:  # directional
            stance = random.choice([Stance.BULLISH, Stance.BULLISH, Stance.BEARISH])
        return StrategyDirective(
            stance=stance,
            focus_fighter_ids=focus,
            aggressiveness=round(random.uniform(0.7, 1.0), 2),
            max_position_per_fighter=random.randint(90, 250),
            reasoning="heuristic fallback (no LLM directive)",
        )
