"""
schemas/drone.py
────────────────
Pydantic models for the Drone domain.

Drones are read-only from the API in Phase 3 — no create/update endpoints
yet. The DroneResponse model covers all list/get operations.
"""
from __future__ import annotations

from datetime import datetime
from typing import Literal

from pydantic import BaseModel, Field


# ── Status enum ───────────────────────────────────────────────────────────────

DroneStatus = Literal[
    "AVAILABLE",
    "BUSY",
    "ASSIGNED",
    "CHARGING",
    "MAINTENANCE",
    "OFFLINE",
]


# ── Response model ────────────────────────────────────────────────────────────

class DroneResponse(BaseModel):
    """Public representation of a drone — returned in list and get endpoints."""

    id: str
    drone_id: str
    model: str
    battery_pct: float = Field(..., ge=0.0, le=100.0)
    max_payload_kg: float
    max_range_km: float
    latitude: float
    longitude: float
    status: DroneStatus
    zone: str
    updated_at: datetime

    model_config = {
        "json_schema_extra": {
            "examples": [
                {
                    "id": "66f1a2b3c4d5e6f7a8b9c0d4",
                    "drone_id": "DRN-001",
                    "model": "Simulated Medical Quadcopter - Class M",
                    "battery_pct": 87.5,
                    "max_payload_kg": 1.75,
                    "max_range_km": 40.0,
                    "latitude": 12.9716,
                    "longitude": 77.5946,
                    "status": "AVAILABLE",
                    "zone": "Central Bengaluru",
                    "updated_at": "2026-08-31T06:00:00Z",
                }
            ]
        }
    }


# ── Internal seed model (used only by seed script) ────────────────────────────

class DroneCreate(BaseModel):
    """Fields required to insert a drone document (seed script only)."""

    drone_id: str
    model: str
    battery_pct: float = Field(..., ge=0.0, le=100.0)
    max_payload_kg: float = Field(..., gt=0.0)
    max_range_km: float = Field(..., gt=0.0)
    latitude: float
    longitude: float
    status: DroneStatus
    zone: str
    updated_at: datetime
