"""
core/config.py
──────────────
Application-wide settings loaded from environment variables (or a .env file).
Uses pydantic-settings so every variable is typed and validated at startup.
A missing required variable raises a clear ValidationError immediately.
"""
from functools import lru_cache
from typing import List

from pydantic import computed_field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """All configurable values for the platform backend."""

    model_config = SettingsConfigDict(
        env_file=(".env", "../.env"),   # look in backend/ then project root
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )

    # ── MongoDB ────────────────────────────────────────────────────────────────
    mongodb_uri: str = "mongodb://localhost:27017"
    mongodb_database: str = "medical_drone_db"

    # ── Auth ───────────────────────────────────────────────────────────────────
    jwt_secret: str = "change_me"
    jwt_access_token_expire_minutes: int = 60   # token TTL in minutes

    # ── ML models ──────────────────────────────────────────────────────────────
    model_path: str = "./models"

    # ── Phase 7, 8 & 9 Agent/Simulation Config ─────────────────────────────────
    priority_wait_threshold_minutes: int = 30
    allocation_safety_factor: float = 1.2
    allocation_min_reserve_pct: float = 15.0
    drone_cruise_speed_kmh: float = 60.0
    drone_battery_consumption_per_km_pct: float = 2.0
    simulation_tick_interval_seconds: float = 2.0
    simulation_speed_multiplier: float = 3.0

    # ── Phase 10: LangGraph + Groq Explanation ─────────────────────────────────
    groq_api_key: str = ""             # set GROQ_API_KEY in .env to enable LLM explanations
    llm_explanation_enabled: bool = True  # set False in CI/tests to skip explanation step

    # ── CORS ───────────────────────────────────────────────────────────────────
    # Stored as a raw comma-separated string; use the `cors_origins_list` property
    # in code so you get a proper List[str].
    # Declared as `str` (not List) so pydantic-settings never attempts JSON parsing.
    cors_origins: str = "http://localhost:5173,http://localhost:3000,http://127.0.0.1:5173,http://127.0.0.1:5174,http://localhost:5174"

    @computed_field  # type: ignore[misc]
    @property
    def cors_origins_list(self) -> List[str]:
        """Return CORS_ORIGINS as a list of stripped origin strings."""
        return [o.strip() for o in self.cors_origins.split(",") if o.strip()]


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    """Return the cached Settings singleton.

    Use FastAPI's Depends(get_settings) in route handlers, or call directly
    inside lifespan / startup code where Depends is unavailable.
    """
    return Settings()
