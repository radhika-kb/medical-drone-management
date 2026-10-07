from typing import List

from fastapi import APIRouter, Depends, HTTPException, Query, status
from motor.motor_asyncio import AsyncIOMotorDatabase

from app.core.deps import get_current_user
from app.db.mongodb import get_database
from app.repositories.mission_repository import get_mission, list_missions
from app.schemas.mission import MissionResponse
from app.schemas.user import UserInDB

router = APIRouter(prefix="/missions", tags=["missions"])
_auth = Depends(get_current_user)


# ── GET /api/missions ─────────────────────────────────────────────────────────

@router.get(
    "",
    response_model=List[MissionResponse],
    summary="List all missions (newest first)",
)
async def list_all_missions(
    skip: int = Query(default=0, ge=0),
    limit: int = Query(default=50, ge=1, le=200),
    db: AsyncIOMotorDatabase = Depends(get_database),  # type: ignore[type-arg]
    _: UserInDB = _auth,
) -> List[MissionResponse]:
    return await list_missions(db, skip=skip, limit=limit)


# ── GET /api/missions/{mission_id} ────────────────────────────────────────────

@router.get(
    "/{mission_id}",
    response_model=MissionResponse,
    summary="Get a mission by ID",
)
async def get_one_mission(
    mission_id: str,
    db: AsyncIOMotorDatabase = Depends(get_database),  # type: ignore[type-arg]
    _: UserInDB = _auth,
) -> MissionResponse:
    mission = await get_mission(db, mission_id)
    if mission is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Mission not found.",
        )
    return mission
