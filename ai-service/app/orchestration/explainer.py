"""
app/orchestration/explainer.py
───────────────────────────────
Read-only LLM explanation step — called AFTER graph.ainvoke() returns.

Contract
--------
- This function is PURELY READ-ONLY with respect to the dispatch outcome.
- It has NO access to write to agent_decisions or any other collection.
- It CANNOT alter which drone is selected, priority assigned, or any decision.
- It returns a natural-language string (or None on failure).
- Any exception is silently caught — the caller NEVER fails because of this.

Implementation
--------------
Uses Groq (llama-3.1-8b-instant) for fast, cheap inference.
The prompt contains only the decision_trail already captured; it does not
inspect raw weather data, drone scores, etc. — so even in the unlikely
event of a bug here, no dispatch data can be mutated.
"""
from __future__ import annotations

import logging
from typing import TYPE_CHECKING, Optional

if TYPE_CHECKING:
    from app.orchestration.state import DispatchState

logger = logging.getLogger(__name__)


def _build_prompt(state: "DispatchState") -> str:
    """Construct a concise prompt from the decision trail."""
    lines = ["You are summarising an AI medical drone dispatch decision for an operator dashboard."]
    lines.append("Below is the step-by-step decision trail from the dispatch pipeline:")
    lines.append("")

    trail = state.get("decision_trail") or []
    for i, entry in enumerate(trail, 1):
        agent    = entry.get("agent", "Unknown")
        decision = entry.get("decision", "")
        reason   = entry.get("reason", "")
        score    = entry.get("score")
        score_str = f" (score: {score:.3f})" if score is not None else ""
        lines.append(f"{i}. [{agent}] Decision: {decision}{score_str} — {reason}")

    lines.append("")
    final = state.get("final_status", "UNKNOWN")
    lines.append(f"Final outcome: {final}")

    mission = state.get("mission")
    if mission:
        lines.append(f"Mission ID: {getattr(mission, 'mission_id', 'N/A')}")
        route_r = state.get("route_result") or {}
        lines.append(
            f"ETA: {route_r.get('eta_minutes', 'N/A'):.1f} min, "
            f"Distance: {route_r.get('distance_km', 'N/A'):.2f} km"
            if isinstance(route_r.get('eta_minutes'), (int, float)) else
            f"ETA: {route_r.get('eta_minutes', 'N/A')}"
        )

    lines.append("")
    lines.append(
        "Write a single concise paragraph (3-5 sentences) summarising the dispatch outcome "
        "for a non-technical operator. Be factual. Do not invent information not in the trail above."
    )
    return "\n".join(lines)


async def generate_explanation(state: "DispatchState") -> Optional[str]:
    """
    Generate a natural-language summary of the dispatch outcome using Groq.

    Returns
    -------
    str   — explanation text on success
    None  — on any failure (missing key, network error, rate limit, etc.)
    """
    try:
        from app.core.config import get_settings
        settings = get_settings()

        groq_api_key = getattr(settings, "groq_api_key", "")
        if not groq_api_key:
            logger.debug("GROQ_API_KEY not set — skipping LLM explanation.")
            return None

        llm_enabled = getattr(settings, "llm_explanation_enabled", True)
        if not llm_enabled:
            logger.debug("LLM explanation disabled via config — skipping.")
            return None

        from langchain_groq import ChatGroq
        from langchain_core.messages import HumanMessage

        llm = ChatGroq(
            model="llama-3.1-8b-instant",
            api_key=groq_api_key,
            temperature=0.3,
            max_tokens=300,
        )

        prompt = _build_prompt(state)
        response = await llm.ainvoke([HumanMessage(content=prompt)])
        explanation = response.content.strip()

        logger.info("LLM explanation generated successfully.")
        return explanation if explanation else None

    except Exception as exc:  # noqa: BLE001
        logger.warning(
            "LLM explanation failed (dispatch outcome unaffected): %s",
            str(exc),
        )
        return None
