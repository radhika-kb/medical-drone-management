"""
repositories/user_repository.py
────────────────────────────────
All database operations for the ``users`` collection.

No business logic lives here — only direct MongoDB reads and writes.
The unique index on ``email`` is created lazily on first write; in
production you would create it via a migration script in ``scripts/``.
"""
from __future__ import annotations

from typing import Optional

from bson import ObjectId
from motor.motor_asyncio import AsyncIOMotorDatabase
from pymongo import ASCENDING, errors as pymongo_errors

from app.core.logging import get_logger
from app.schemas.user import UserInDB

logger = get_logger(__name__)

_COLLECTION = "users"


# ── Index bootstrap ───────────────────────────────────────────────────────────

async def ensure_indexes(db: AsyncIOMotorDatabase) -> None:  # type: ignore[type-arg]
    """Create unique index on email if it does not already exist.

    Safe to call repeatedly — ``pymongo`` skips creation if the index exists.
    """
    await db[_COLLECTION].create_index(
        [("email", ASCENDING)],
        unique=True,
    )
    logger.info("User collection indexes verified")


# ── Write operations ──────────────────────────────────────────────────────────

async def create_user(
    db: AsyncIOMotorDatabase,  # type: ignore[type-arg]
    user: UserInDB,
) -> UserInDB:
    """Insert a new user document and return it with the assigned ``id``.

    Raises:
        :class:`pymongo.errors.DuplicateKeyError` if a user with the same
        email already exists (caller is responsible for mapping to HTTP 409).
    """
    doc = user.to_mongo()
    result = await db[_COLLECTION].insert_one(doc)
    user.id = str(result.inserted_id)
    logger.info("User created", extra={"user_id": user.id, "email": user.email})
    return user


# ── Read operations ───────────────────────────────────────────────────────────

async def get_user_by_email(
    db: AsyncIOMotorDatabase,  # type: ignore[type-arg]
    email: str,
) -> Optional[UserInDB]:
    """Return the user with the given email, or ``None`` if not found."""
    doc = await db[_COLLECTION].find_one({"email": email})
    return _doc_to_user(doc)


async def get_user_by_id(
    db: AsyncIOMotorDatabase,  # type: ignore[type-arg]
    user_id: str,
) -> Optional[UserInDB]:
    """Return the user with the given ``_id`` string, or ``None``."""
    try:
        oid = ObjectId(user_id)
    except Exception:  # noqa: BLE001
        return None
    doc = await db[_COLLECTION].find_one({"_id": oid})
    return _doc_to_user(doc)


# ── Helpers ───────────────────────────────────────────────────────────────────

def _doc_to_user(doc: Optional[dict]) -> Optional[UserInDB]:
    """Convert a raw MongoDB document to a ``UserInDB`` instance."""
    if doc is None:
        return None
    doc["_id"] = str(doc["_id"])
    return UserInDB.model_validate(doc)
