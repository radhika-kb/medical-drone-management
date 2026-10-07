"""
repositories/medical_center_repository.py
──────────────────────────────────────────
All database operations for the ``medical_centers`` collection.
"""
from __future__ import annotations

from datetime import datetime, timezone
from typing import Optional

from bson import ObjectId
from motor.motor_asyncio import AsyncIOMotorDatabase

from app.core.logging import get_logger
from app.schemas.medical_center import (
    MedicalCenterCreate,
    MedicalCenterResponse,
    MedicalCenterUpdate,
)

logger = get_logger(__name__)

_COLLECTION = "medical_centers"


# ── Write operations ──────────────────────────────────────────────────────────

async def create_medical_center(
    db: AsyncIOMotorDatabase,  # type: ignore[type-arg]
    data: MedicalCenterCreate,
) -> MedicalCenterResponse:
    """Insert a new medical center and return the full response document."""
    doc = data.model_dump()
    doc["created_at"] = datetime.now(tz=timezone.utc)

    result = await db[_COLLECTION].insert_one(doc)
    doc["id"] = str(result.inserted_id)

    logger.info("Medical center created", extra={"id": doc["id"], "mc_name": doc["name"]})
    return MedicalCenterResponse.model_validate(doc)


async def update_medical_center(
    db: AsyncIOMotorDatabase,  # type: ignore[type-arg]
    mc_id: str,
    data: MedicalCenterUpdate,
) -> Optional[MedicalCenterResponse]:
    """Partially update a medical center. Returns ``None`` if not found."""
    oid = _to_object_id(mc_id)
    if oid is None:
        return None

    # Only include fields explicitly set by the caller (exclude unset/None)
    updates = data.model_dump(exclude_unset=True, exclude_none=True)
    if not updates:
        # Nothing to change — just return the existing document
        return await get_medical_center(db, mc_id)

    result = await db[_COLLECTION].find_one_and_update(
        {"_id": oid},
        {"$set": updates},
        return_document=True,  # return the updated doc
    )
    return _doc_to_response(result)


async def delete_medical_center(
    db: AsyncIOMotorDatabase,  # type: ignore[type-arg]
    mc_id: str,
) -> bool:
    """Delete a medical center. Returns ``True`` if deleted, ``False`` if not found."""
    oid = _to_object_id(mc_id)
    if oid is None:
        return False
    result = await db[_COLLECTION].delete_one({"_id": oid})
    deleted = result.deleted_count > 0
    if deleted:
        logger.info("Medical center deleted", extra={"id": mc_id})
    return deleted


# ── Read operations ───────────────────────────────────────────────────────────

async def get_medical_center(
    db: AsyncIOMotorDatabase,  # type: ignore[type-arg]
    mc_id: str,
) -> Optional[MedicalCenterResponse]:
    """Return a medical center by id, or ``None`` if not found."""
    oid = _to_object_id(mc_id)
    if oid is None:
        return None
    doc = await db[_COLLECTION].find_one({"_id": oid})
    return _doc_to_response(doc)


async def list_medical_centers(
    db: AsyncIOMotorDatabase,  # type: ignore[type-arg]
    skip: int = 0,
    limit: int = 20,
) -> list[MedicalCenterResponse]:
    """Return a paginated list of medical centers, sorted by name."""
    cursor = db[_COLLECTION].find().sort("name", 1).skip(skip).limit(limit)
    results = []
    async for doc in cursor:
        mc = _doc_to_response(doc)
        if mc is not None:
            results.append(mc)
    return results


# ── Helpers ───────────────────────────────────────────────────────────────────

def _to_object_id(mc_id: str) -> Optional[ObjectId]:
    try:
        return ObjectId(mc_id)
    except Exception:  # noqa: BLE001
        return None


def _doc_to_response(doc: Optional[dict]) -> Optional[MedicalCenterResponse]:
    if doc is None:
        return None
    doc["id"] = str(doc.pop("_id"))
    # Provide a fallback for legacy documents that may lack created_at
    if "created_at" not in doc:
        doc["created_at"] = datetime.now(tz=timezone.utc)
    return MedicalCenterResponse.model_validate(doc)
