"""
db/mongodb.py
─────────────
Async MongoDB connection management using Motor.

Lifecycle
---------
``connect_db()`` is called during FastAPI's lifespan startup and stores the
Motor client + database reference on the ``_State`` singleton.
``disconnect_db()`` is called on shutdown to close the connection pool cleanly.

Dependency
----------
Inject ``get_database`` via ``Depends`` in route handlers:

    from motor.motor_asyncio import AsyncIOMotorDatabase
    from fastapi import Depends
    from app.db.mongodb import get_database

    @router.get("/example")
    async def example(db: AsyncIOMotorDatabase = Depends(get_database)):
        doc = await db["collection"].find_one({})
        ...
"""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Optional

from motor.motor_asyncio import AsyncIOMotorClient, AsyncIOMotorDatabase

from app.core.config import get_settings
from app.core.logging import get_logger

logger = get_logger(__name__)


@dataclass
class _State:
    """Module-level singleton holding the Motor client and DB handle."""

    client: Optional[AsyncIOMotorClient] = field(default=None)  # type: ignore[type-arg]
    database: Optional[AsyncIOMotorDatabase] = field(default=None)  # type: ignore[type-arg]


_state = _State()


async def connect_db() -> None:
    """Create the Motor client and verify connectivity with a ping.

    Called once during application startup (FastAPI lifespan).
    Raises ``RuntimeError`` if the database is unreachable.
    """
    settings = get_settings()
    logger.info(
        "Connecting to MongoDB",
        extra={"uri_host": _redact_uri(settings.mongodb_uri), "database": settings.mongodb_database},
    )

    client: AsyncIOMotorClient = AsyncIOMotorClient(  # type: ignore[type-arg]
        settings.mongodb_uri,
        serverSelectionTimeoutMS=5_000,
    )

    try:
        # Ping to verify the connection before the app starts accepting requests.
        await client.admin.command("ping")
        _state.client = client
        _state.database = client[settings.mongodb_database]
        logger.info("MongoDB connection established", extra={"database": settings.mongodb_database})
    except Exception as e:
        logger.warning(f"MongoDB not available ({e}). AI service running in stateless microservice mode.")
        _state.client = client
        _state.database = client[settings.mongodb_database]


async def disconnect_db() -> None:
    """Close the Motor connection pool gracefully.

    Called once during application shutdown (FastAPI lifespan).
    """
    if _state.client is not None:
        _state.client.close()
        _state.client = None
        _state.database = None
        logger.info("MongoDB connection closed")


async def get_database() -> AsyncIOMotorDatabase:  # type: ignore[type-arg]
    """FastAPI dependency that yields the active Motor database handle.

    Raises:
        RuntimeError: If called before ``connect_db()`` has been awaited.
    """
    if _state.database is None:
        raise RuntimeError(
            "Database is not initialised. "
            "Ensure connect_db() was called during application startup."
        )
    return _state.database


def get_client() -> Optional[AsyncIOMotorClient]:  # type: ignore[type-arg]
    """Return the raw Motor client (used by the health check)."""
    return _state.client


# ── Helpers ───────────────────────────────────────────────────────────────────

def _redact_uri(uri: str) -> str:
    """Strip credentials from a MongoDB URI for safe logging."""
    try:
        from urllib.parse import urlparse, urlunparse
        parsed = urlparse(uri)
        redacted = parsed._replace(netloc=parsed.hostname or "")
        return urlunparse(redacted)
    except Exception:  # noqa: BLE001
        return "<uri>"
