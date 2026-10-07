"""
repositories/drone_repository.py
─────────────────────────────────
All database operations for the ``drones`` collection.
"""
from __future__ import annotations

from typing import Optional

from bson import ObjectId
from motor.motor_asyncio import AsyncIOMotorDatabase

from app.core.logging import get_logger
from app.schemas.drone import DroneCreate, DroneResponse

logger = get_logger(__name__)

_COLLECTION = "drones"


# ── Helpers ───────────────────────────────────────────────────────────────────

def _to_oid(id_str: str) -> Optional[ObjectId]:
    try:
        return ObjectId(id_str)
    except Exception:  # noqa: BLE001
        return None


def _doc_to_response(doc: Optional[dict]) -> Optional[DroneResponse]:
    if doc is None:
        return None
    doc["id"] = str(doc.pop("_id"))
    return DroneResponse.model_validate(doc)


# ── Read ──────────────────────────────────────────────────────────────────────

async def get_drone(
    db: AsyncIOMotorDatabase,  # type: ignore[type-arg]
    drone_id: str,
) -> Optional[DroneResponse]:
    """Return a drone by its MongoDB _id string."""
    oid = _to_oid(drone_id)
    if oid is None:
        return None
    doc = await db[_COLLECTION].find_one({"_id": oid})
    return _doc_to_response(doc)

async def get_drone_by_drone_id(
    db: AsyncIOMotorDatabase,
    drone_id_str: str,
) -> Optional[DroneResponse]:
    """Return a drone by its business ID string (e.g., DRN-001)."""
    doc = await db[_COLLECTION].find_one({"drone_id": drone_id_str})
    return _doc_to_response(doc)


async def list_drones(
    db: AsyncIOMotorDatabase,  # type: ignore[type-arg]
    skip: int = 0,
    limit: int = 50,
) -> list[DroneResponse]:
    """Return a paginated list of all drones, sorted by drone_id."""
    cursor = db[_COLLECTION].find().sort("drone_id", 1).skip(skip).limit(limit)
    results = []
    async for doc in cursor:
        d = _doc_to_response(doc)
        if d is not None:
            results.append(d)
    return results


async def list_available_drones(
    db: AsyncIOMotorDatabase,  # type: ignore[type-arg]
) -> list[DroneResponse]:
    """Return all drones whose status is AVAILABLE."""
    cursor = db[_COLLECTION].find({"status": "AVAILABLE"}).sort("drone_id", 1)
    results = []
    async for doc in cursor:
        d = _doc_to_response(doc)
        if d is not None:
            results.append(d)
    return results


# ── Write (used by seed script only in Phase 3) ───────────────────────────────

async def insert_many_drones(
    db: AsyncIOMotorDatabase,  # type: ignore[type-arg]
    drones: list[DroneCreate],
) -> int:
    """Bulk-insert drone documents. Returns the count inserted."""
    docs = [d.model_dump() for d in drones]
    result = await db[_COLLECTION].insert_many(docs)
    inserted = len(result.inserted_ids)
    logger.info("Drones seeded", extra={"count": inserted})
    return inserted


async def count_drones(db: AsyncIOMotorDatabase) -> int:  # type: ignore[type-arg]
    """Return total number of drone documents (used by seed to detect existing data)."""
    return await db[_COLLECTION].count_documents({})

async def update_drone_status(db: AsyncIOMotorDatabase, drone_id_str: str, status: str) -> bool:
    """Update a drone's status."""
    oid = _to_oid(drone_id_str)
    if not oid:
        return False
        
    result = await db[_COLLECTION].update_one(
        {"_id": oid},
        {"$set": {"status": status}}
    )
    return result.modified_count > 0
