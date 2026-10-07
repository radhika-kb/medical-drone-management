"""
repositories/medical_request_repository.py
────────────────────────────────────────────
All database operations for the ``medical_requests`` collection.
"""
from __future__ import annotations

from datetime import datetime, timezone
from typing import Optional

from bson import ObjectId
from motor.motor_asyncio import AsyncIOMotorDatabase

from app.core.logging import get_logger
from app.schemas.medical_request import (
    MedicalRequestCreate,
    MedicalRequestResponse,
    RequestStatus,
    Urgency,
    MedicalType,
)

logger = get_logger(__name__)

_COLLECTION = "medical_requests"


# ── Helpers ───────────────────────────────────────────────────────────────────

def _to_oid(id_str: str) -> Optional[ObjectId]:
    try:
        return ObjectId(id_str)
    except Exception:  # noqa: BLE001
        return None


def _doc_to_response(doc: Optional[dict]) -> Optional[MedicalRequestResponse]:
    if doc is None:
        return None
    doc["id"] = str(doc.pop("_id"))
    return MedicalRequestResponse.model_validate(doc)


def _generate_request_id() -> str:
    """Generate a human-readable request ID like REQ-20260831-abc12f."""
    from datetime import datetime, timezone
    import secrets
    date_part = datetime.now(tz=timezone.utc).strftime("%Y%m%d")
    rand_part = secrets.token_hex(3)          # 6 hex chars, collision-resistant enough
    return f"REQ-{date_part}-{rand_part}"


# ── Write ─────────────────────────────────────────────────────────────────────

async def create_request(
    db: AsyncIOMotorDatabase,  # type: ignore[type-arg]
    data: MedicalRequestCreate,
) -> MedicalRequestResponse:
    """Insert a new medical request document with status PENDING."""
    now = datetime.now(tz=timezone.utc)
    doc = {
        "request_id": _generate_request_id(),
        "center_id": data.center_id,
        "medical_type": data.medical_type,
        "quantity": data.quantity,
        "weight_kg": data.weight_kg,
        "urgency": data.urgency,
        "pickup": data.pickup.model_dump(),
        "destination": data.destination.model_dump(),
        "status": "PENDING",
        "created_at": now,
    }
    result = await db[_COLLECTION].insert_one(doc)
    doc["id"] = str(result.inserted_id)

    logger.info(
        "Medical request created",
        extra={
            "request_id": doc["request_id"],
            "center_id": data.center_id,
            "urgency": data.urgency,
        },
    )
    return MedicalRequestResponse.model_validate(doc)


# ── Read ──────────────────────────────────────────────────────────────────────

async def update_request_status(
    db: AsyncIOMotorDatabase,  # type: ignore[type-arg]
    request_id: str,
    status: RequestStatus,
) -> bool:
    """Update the status of a medical request."""
    # Handle both MongoDB _id string and the human-readable request_id
    oid = _to_oid(request_id)
    query = {"_id": oid} if oid else {"request_id": request_id}
    
    result = await db[_COLLECTION].update_one(query, {"$set": {"status": status}})
    return result.modified_count > 0

async def get_request(
    db: AsyncIOMotorDatabase,  # type: ignore[type-arg]
    request_id: str,
) -> Optional[MedicalRequestResponse]:
    """Return a single request by its MongoDB _id string."""
    oid = _to_oid(request_id)
    if oid is None:
        return None
    doc = await db[_COLLECTION].find_one({"_id": oid})
    return _doc_to_response(doc)


async def list_requests(
    db: AsyncIOMotorDatabase,  # type: ignore[type-arg]
    status: Optional[RequestStatus] = None,
    center_id: Optional[str] = None,
    urgency: Optional[Urgency] = None,
    skip: int = 0,
    limit: int = 20,
) -> list[MedicalRequestResponse]:
    """Return a paginated, optionally filtered list of requests."""
    query: dict = {}
    if status is not None:
        query["status"] = status
    if center_id is not None:
        query["center_id"] = center_id
    if urgency is not None:
        query["urgency"] = urgency

    cursor = (
        db[_COLLECTION]
        .find(query)
        .sort("created_at", -1)   # newest first
        .skip(skip)
        .limit(limit)
    )
    results = []
    async for doc in cursor:
        r = _doc_to_response(doc)
        if r is not None:
            results.append(r)
    return results
