"""
schemas/medical_request.py
──────────────────────────
Pydantic models for the MedicalRequest domain.

    GeoPoint             →  {lat, lng} sub-model reused in pickup / destination
    MedicalRequestCreate →  input for POST /api/requests
    MedicalRequestResponse → returned to the client
"""
from __future__ import annotations

from datetime import datetime, timezone
from typing import Literal, Optional

from pydantic import BaseModel, Field


# ── Enums (as Literals for schema clarity) ────────────────────────────────────
from enum import Enum

class MedicalType(str, Enum):
    BLOOD = "BLOOD"
    ORGANS = "ORGANS"
    VACCINES = "VACCINES"
    EMERGENCY_MEDICINE = "EMERGENCY_MEDICINE"
    PLASMA = "PLASMA"
    INSULIN = "INSULIN"
    OTHER = "OTHER"

Urgency = Literal["LOW", "MEDIUM", "HIGH", "CRITICAL"]

RequestStatus = Literal[
    "PENDING",
    "ASSIGNED",
    "IN_TRANSIT",
    "DELIVERED",
    "CANCELLED",
]


# ── Sub-model ─────────────────────────────────────────────────────────────────

class GeoPoint(BaseModel):
    """A geographic coordinate pair."""

    lat: float = Field(..., ge=-90.0, le=90.0, examples=[12.9716])
    lng: float = Field(..., ge=-180.0, le=180.0, examples=[77.5946])


# ── Input model ───────────────────────────────────────────────────────────────

class MedicalRequestCreate(BaseModel):
    """Fields submitted when creating a new delivery request."""

    center_id: str = Field(
        ...,
        description="ID of the originating medical center (must exist in DB)",
        examples=["66f1a2b3c4d5e6f7a8b9c0d2"],
    )
    medical_type: MedicalType = Field(..., examples=["BLOOD"])
    quantity: int = Field(..., gt=0, description="Must be at least 1", examples=[5])
    weight_kg: float = Field(
        ..., gt=0.0, description="Total payload weight; must be > 0 kg", examples=[1.2]
    )
    urgency: Urgency = Field(default="MEDIUM", examples=["HIGH"])
    pickup: GeoPoint = Field(..., description="Pickup coordinates")
    destination: GeoPoint = Field(..., description="Delivery destination coordinates")


# ── Response model ────────────────────────────────────────────────────────────

class MedicalRequestResponse(BaseModel):
    """Full request document returned to the client."""

    id: str
    request_id: str
    center_id: str
    medical_type: MedicalType
    quantity: int
    weight_kg: float
    urgency: Urgency
    pickup: GeoPoint
    destination: GeoPoint
    status: RequestStatus
    created_at: datetime

    model_config = {
        "json_schema_extra": {
            "examples": [
                {
                    "id": "66f1a2b3c4d5e6f7a8b9c0d3",
                    "request_id": "REQ-20260831-0001",
                    "center_id": "66f1a2b3c4d5e6f7a8b9c0d2",
                    "medical_type": "BLOOD",
                    "quantity": 5,
                    "weight_kg": 1.2,
                    "urgency": "HIGH",
                    "pickup": {"lat": 12.97, "lng": 77.59},
                    "destination": {"lat": 12.98, "lng": 77.61},
                    "status": "PENDING",
                    "created_at": "2026-08-31T06:00:00Z",
                }
            ]
        }
    }
