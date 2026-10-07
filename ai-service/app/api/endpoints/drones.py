"""
api/endpoints/drones.py
────────────────────────
Drone fleet read endpoints. All routes require a valid JWT.

IMPORTANT — route registration order:
  /available  must be registered BEFORE  /{drone_id}
  so FastAPI does not interpret the literal string "available" as an ID.

GET /api/drones             →  list full fleet (paginated)
GET /api/drones/available   →  only AVAILABLE drones
"""
from typing import List

from fastapi import APIRouter, Depends, Query, status
from motor.motor_asyncio import AsyncIOMotorDatabase

from app.core.deps import get_current_user
from app.db.mongodb import get_database
from app.repositories.drone_repository import list_available_drones, list_drones
from app.schemas.drone import DroneResponse
from app.schemas.user import UserInDB

router = APIRouter(prefix="/drones", tags=["drones"])

_auth = Depends(get_current_user)


# ── GET /api/drones ───────────────────────────────────────────────────────────

@router.get(
    "",
    response_model=List[DroneResponse],
    summary="List all drones in the fleet",
)
async def list_fleet(
    skip: int = Query(default=0, ge=0),
    limit: int = Query(default=50, ge=1, le=200),
    db: AsyncIOMotorDatabase = Depends(get_database),  # type: ignore[type-arg]
    _: UserInDB = _auth,
) -> List[DroneResponse]:
    return await list_drones(db, skip=skip, limit=limit)


# ── GET /api/drones/available  (must come BEFORE /{drone_id}) ────────────────

@router.get(
    "/available",
    response_model=List[DroneResponse],
    summary="List only AVAILABLE drones",
    description=(
        "Returns drones whose `status` is **AVAILABLE**. "
        "Registered before `/{drone_id}` to avoid route shadowing."
    ),
)
async def list_available(
    db: AsyncIOMotorDatabase = Depends(get_database),  # type: ignore[type-arg]
    _: UserInDB = _auth,
) -> List[DroneResponse]:
    return await list_available_drones(db)
