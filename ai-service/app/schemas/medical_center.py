"""
schemas/medical_center.py
──────────────────────────
Pydantic models for the MedicalCenter domain.

    MedicalCenterCreate   →  input for POST / PUT
    MedicalCenterUpdate   →  partial input for PATCH (all fields Optional)
    MedicalCenterResponse →  returned to the client
"""
from __future__ import annotations

from datetime import datetime, timezone
from typing import Literal, Optional

from pydantic import BaseModel, Field, field_validator


# ── Type constraint ───────────────────────────────────────────────────────────

MedicalCenterType = Literal["hospital", "clinic", "pharmacy", "blood_bank"]


# ── Input model ───────────────────────────────────────────────────────────────

class MedicalCenterCreate(BaseModel):
    """Fields required when creating a medical center."""

    name: str = Field(..., min_length=2, max_length=200, examples=["City General Hospital"])
    type: MedicalCenterType = Field(..., examples=["hospital"])
    address: str = Field(..., min_length=5, examples=["123 MG Road, Pune"])
    latitude: float = Field(..., ge=-90.0, le=90.0, examples=[18.5204])
    longitude: float = Field(..., ge=-180.0, le=180.0, examples=[73.8567])
    contact: str = Field(..., min_length=5, max_length=50, examples=["+91-9999999999"])

    @field_validator("latitude", "longitude")
    @classmethod
    def _round_coords(cls, v: float) -> float:
        """Store coordinates with at most 6 decimal places."""
        return round(v, 6)


# ── Partial update model ──────────────────────────────────────────────────────

class MedicalCenterUpdate(BaseModel):
    """All fields optional so PATCH updates only what is provided."""

    name: Optional[str] = Field(default=None, min_length=2, max_length=200)
    type: Optional[MedicalCenterType] = None
    address: Optional[str] = Field(default=None, min_length=5)
    latitude: Optional[float] = Field(default=None, ge=-90.0, le=90.0)
    longitude: Optional[float] = Field(default=None, ge=-180.0, le=180.0)
    contact: Optional[str] = Field(default=None, min_length=5, max_length=50)

    @field_validator("latitude", "longitude")
    @classmethod
    def _round_coords(cls, v: Optional[float]) -> Optional[float]:
        return round(v, 6) if v is not None else v


# ── Response model ────────────────────────────────────────────────────────────

class MedicalCenterResponse(BaseModel):
    """Public representation of a medical center."""

    id: str
    name: str
    type: MedicalCenterType
    address: str
    latitude: float
    longitude: float
    contact: str
    created_at: datetime

    model_config = {
        "json_schema_extra": {
            "examples": [
                {
                    "id": "66f1a2b3c4d5e6f7a8b9c0d2",
                    "name": "City General Hospital",
                    "type": "hospital",
                    "address": "123 MG Road, Pune",
                    "latitude": 18.5204,
                    "longitude": 73.8567,
                    "contact": "+91-9999999999",
                    "created_at": "2026-08-31T06:00:00Z",
                }
            ]
        }
    }
