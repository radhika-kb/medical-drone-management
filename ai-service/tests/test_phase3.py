"""
tests/test_phase3.py
─────────────────────
Unit tests for Phase 3 — Medical Requests and Drones.

All tests are isolated: no live MongoDB connection required.
Repository / DB calls are mocked with AsyncMock.

Run with:
    cd backend
    python -m pytest tests/test_phase3.py -v
"""
from __future__ import annotations

import sys
import os

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from datetime import datetime, timezone
from unittest.mock import AsyncMock, patch

import pytest
from fastapi import HTTPException
from pydantic import ValidationError

from app.schemas.medical_request import GeoPoint, MedicalRequestCreate


# ═══════════════════════════════════════════════════════════════════════════════
# MedicalRequestCreate — Pydantic validation (no DB needed)
# ═══════════════════════════════════════════════════════════════════════════════

_VALID_PAYLOAD = dict(
    center_id="66f1a2b3c4d5e6f7a8b9c0d2",
    medical_type="BLOOD",
    quantity=5,
    weight_kg=1.2,
    urgency="HIGH",
    pickup={"lat": 12.97, "lng": 77.59},
    destination={"lat": 12.98, "lng": 77.61},
)


class TestMedicalRequestSchema:
    def test_valid_payload_passes(self):
        """A well-formed payload constructs without errors."""
        req = MedicalRequestCreate(**_VALID_PAYLOAD)
        assert req.quantity == 5
        assert req.weight_kg == 1.2

    def test_zero_quantity_fails(self):
        """quantity=0 must fail Pydantic gt=0 validation."""
        with pytest.raises(ValidationError) as exc_info:
            MedicalRequestCreate(**{**_VALID_PAYLOAD, "quantity": 0})
        errors = exc_info.value.errors()
        assert any(e["loc"] == ("quantity",) for e in errors)

    def test_negative_quantity_fails(self):
        """quantity=-1 must fail Pydantic gt=0 validation."""
        with pytest.raises(ValidationError) as exc_info:
            MedicalRequestCreate(**{**_VALID_PAYLOAD, "quantity": -1})
        errors = exc_info.value.errors()
        assert any(e["loc"] == ("quantity",) for e in errors)

    def test_zero_weight_fails(self):
        """weight_kg=0.0 must fail Pydantic gt=0 validation."""
        with pytest.raises(ValidationError) as exc_info:
            MedicalRequestCreate(**{**_VALID_PAYLOAD, "weight_kg": 0.0})
        errors = exc_info.value.errors()
        assert any(e["loc"] == ("weight_kg",) for e in errors)

    def test_negative_weight_fails(self):
        """weight_kg=-1.5 must fail Pydantic gt=0 validation."""
        with pytest.raises(ValidationError) as exc_info:
            MedicalRequestCreate(**{**_VALID_PAYLOAD, "weight_kg": -1.5})
        errors = exc_info.value.errors()
        assert any(e["loc"] == ("weight_kg",) for e in errors)

    def test_invalid_medical_type_fails(self):
        """An unrecognised medical_type must fail validation."""
        with pytest.raises(ValidationError):
            MedicalRequestCreate(**{**_VALID_PAYLOAD, "medical_type": "radioactive"})

    def test_invalid_urgency_fails(self):
        """An unrecognised urgency level must fail validation."""
        with pytest.raises(ValidationError):
            MedicalRequestCreate(**{**_VALID_PAYLOAD, "urgency": "ULTRA"})

    def test_geopoint_lat_out_of_range_fails(self):
        """Latitude outside [-90, 90] must fail."""
        with pytest.raises(ValidationError):
            MedicalRequestCreate(**{**_VALID_PAYLOAD, "pickup": {"lat": 91.0, "lng": 77.0}})

    def test_geopoint_lng_out_of_range_fails(self):
        """Longitude outside [-180, 180] must fail."""
        with pytest.raises(ValidationError):
            MedicalRequestCreate(**{**_VALID_PAYLOAD, "destination": {"lat": 12.0, "lng": 181.0}})


# ═══════════════════════════════════════════════════════════════════════════════
# POST /api/requests — invalid center_id returns 404
# ═══════════════════════════════════════════════════════════════════════════════

class TestCreateRequestEndpoint:
    @pytest.mark.asyncio
    async def test_invalid_center_id_returns_404(self):
        """POST with a non-existent center_id must raise HTTP 404."""
        from app.api.endpoints.medical_requests import create

        payload = MedicalRequestCreate(**_VALID_PAYLOAD)

        # Patch get_medical_center to return None (center not found)
        with patch(
            "app.api.endpoints.medical_requests.get_medical_center",
            new_callable=AsyncMock,
            return_value=None,
        ):
            mock_db = AsyncMock()
            with pytest.raises(HTTPException) as exc_info:
                await create(payload=payload, db=mock_db, _=AsyncMock())

        assert exc_info.value.status_code == 404
        assert "not found" in exc_info.value.detail.lower()

    @pytest.mark.asyncio
    async def test_valid_center_id_creates_request(self):
        """POST with a valid center_id calls create_request and returns 201 body."""
        from app.api.endpoints.medical_requests import create
        from app.schemas.medical_request import MedicalRequestResponse

        payload = MedicalRequestCreate(**_VALID_PAYLOAD)

        fake_response = MedicalRequestResponse(
            id="abc123",
            request_id="REQ-20260831-abc123",
            center_id=payload.center_id,
            medical_type=payload.medical_type,
            quantity=payload.quantity,
            weight_kg=payload.weight_kg,
            urgency=payload.urgency,
            pickup=payload.pickup,
            destination=payload.destination,
            status="PENDING",
            created_at=datetime.now(tz=timezone.utc),
        )

        with patch(
            "app.api.endpoints.medical_requests.get_medical_center",
            new_callable=AsyncMock,
            return_value=object(),            # truthy — center exists
        ), patch(
            "app.api.endpoints.medical_requests.create_request",
            new_callable=AsyncMock,
            return_value=fake_response,
        ):
            mock_db = AsyncMock()
            result = await create(payload=payload, db=mock_db, _=AsyncMock())

        assert result.status == "PENDING"
        assert result.request_id.startswith("REQ-")


# ═══════════════════════════════════════════════════════════════════════════════
# GET /api/drones/available — excludes non-AVAILABLE drones
# ═══════════════════════════════════════════════════════════════════════════════

class TestAvailableDronesEndpoint:
    @pytest.mark.asyncio
    async def test_returns_only_available_drones(self):
        """list_available endpoint returns only drones whose status is AVAILABLE."""
        from app.api.endpoints.drones import list_available
        from app.schemas.drone import DroneResponse

        now = datetime.now(tz=timezone.utc)

        def _make_drone(drone_id: str, drone_status: str) -> DroneResponse:
            return DroneResponse(
                id=f"id_{drone_id}",
                drone_id=drone_id,
                model="Test Model",
                battery_pct=80.0,
                max_payload_kg=2.0,
                max_range_km=30.0,
                latitude=12.97,
                longitude=77.59,
                status=drone_status,  # type: ignore[arg-type]
                zone="Test Zone",
                updated_at=now,
            )

        # 4 drones, only 2 are AVAILABLE
        available_only = [
            _make_drone("DRN-001", "AVAILABLE"),
            _make_drone("DRN-003", "AVAILABLE"),
        ]

        with patch(
            "app.api.endpoints.drones.list_available_drones",
            new_callable=AsyncMock,
            return_value=available_only,
        ):
            mock_db = AsyncMock()
            result = await list_available(db=mock_db, _=AsyncMock())

        assert len(result) == 2
        assert all(d.status == "AVAILABLE" for d in result)

    @pytest.mark.asyncio
    async def test_returns_empty_list_when_none_available(self):
        """Returns an empty list when no drones are AVAILABLE."""
        from app.api.endpoints.drones import list_available

        with patch(
            "app.api.endpoints.drones.list_available_drones",
            new_callable=AsyncMock,
            return_value=[],
        ):
            result = await list_available(db=AsyncMock(), _=AsyncMock())

        assert result == []
