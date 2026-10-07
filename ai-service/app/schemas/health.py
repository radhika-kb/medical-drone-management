"""
schemas/health.py
─────────────────
Pydantic response models for the health-check endpoint.
"""
from typing import Literal

from pydantic import BaseModel, Field


class HealthResponse(BaseModel):
    """Shape of the GET /api/health response body."""

    status: Literal["ok", "error"] = Field(
        ...,
        description="Overall service health. 'ok' means all checks passed.",
    )
    mongo: Literal["connected", "disconnected"] = Field(
        ...,
        description="MongoDB connectivity status.",
    )
    version: str = Field(
        default="0.1.0",
        description="Backend API version string.",
    )

    model_config = {
        "json_schema_extra": {
            "examples": [
                {"status": "ok", "mongo": "connected", "version": "0.1.0"},
                {"status": "error", "mongo": "disconnected", "version": "0.1.0"},
            ]
        }
    }
