"""
api/endpoints/medical_centers.py
─────────────────────────────────
CRUD endpoints for medical centers. All routes require a valid JWT.

POST   /api/medical-centers           →  create  (201)
GET    /api/medical-centers           →  list    (200, paginated)
GET    /api/medical-centers/{id}      →  get one (200 / 404)
PATCH  /api/medical-centers/{id}      →  partial update (200 / 404)
DELETE /api/medical-centers/{id}      →  delete  (204 / 404)
"""
from typing import List

from fastapi import APIRouter, Depends, HTTPException, Query, status
from motor.motor_asyncio import AsyncIOMotorDatabase

from app.core.deps import get_current_user
from app.core.logging import get_logger
from app.db.mongodb import get_database
from app.repositories.medical_center_repository import (
    create_medical_center,
    delete_medical_center,
    get_medical_center,
    list_medical_centers,
    update_medical_center,
)
from app.schemas.medical_center import (
    MedicalCenterCreate,
    MedicalCenterResponse,
    MedicalCenterUpdate,
)
from app.schemas.user import UserInDB

logger = get_logger(__name__)

router = APIRouter(prefix="/medical-centers", tags=["medical-centers"])

# Shorthand dependency tuple used by every route in this router
_auth = Depends(get_current_user)


# ── POST /api/medical-centers ─────────────────────────────────────────────────

@router.post(
    "",
    response_model=MedicalCenterResponse,
    status_code=status.HTTP_201_CREATED,
    summary="Create a medical center",
)
async def create(
    payload: MedicalCenterCreate,
    db: AsyncIOMotorDatabase = Depends(get_database),  # type: ignore[type-arg]
    _: UserInDB = _auth,
) -> MedicalCenterResponse:
    mc = await create_medical_center(db, payload)
    logger.info("Medical center created via API", extra={"id": mc.id})
    return mc


# ── GET /api/medical-centers ──────────────────────────────────────────────────

@router.get(
    "",
    response_model=List[MedicalCenterResponse],
    summary="List all medical centers",
)
async def list_all(
    skip: int = Query(default=0, ge=0, description="Number of records to skip"),
    limit: int = Query(default=20, ge=1, le=100, description="Max records to return"),
    db: AsyncIOMotorDatabase = Depends(get_database),  # type: ignore[type-arg]
    _: UserInDB = _auth,
) -> List[MedicalCenterResponse]:
    return await list_medical_centers(db, skip=skip, limit=limit)


# ── GET /api/medical-centers/{id} ────────────────────────────────────────────

@router.get(
    "/{mc_id}",
    response_model=MedicalCenterResponse,
    summary="Get a medical center by ID",
)
async def get_one(
    mc_id: str,
    db: AsyncIOMotorDatabase = Depends(get_database),  # type: ignore[type-arg]
    _: UserInDB = _auth,
) -> MedicalCenterResponse:
    mc = await get_medical_center(db, mc_id)
    if mc is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Medical center not found")
    return mc


# ── PATCH /api/medical-centers/{id} ──────────────────────────────────────────

@router.patch(
    "/{mc_id}",
    response_model=MedicalCenterResponse,
    summary="Partially update a medical center",
)
async def patch(
    mc_id: str,
    payload: MedicalCenterUpdate,
    db: AsyncIOMotorDatabase = Depends(get_database),  # type: ignore[type-arg]
    _: UserInDB = _auth,
) -> MedicalCenterResponse:
    mc = await update_medical_center(db, mc_id, payload)
    if mc is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Medical center not found")
    return mc


# ── DELETE /api/medical-centers/{id} ─────────────────────────────────────────

@router.delete(
    "/{mc_id}",
    status_code=status.HTTP_204_NO_CONTENT,
    summary="Delete a medical center",
)
async def delete(
    mc_id: str,
    db: AsyncIOMotorDatabase = Depends(get_database),  # type: ignore[type-arg]
    _: UserInDB = _auth,
) -> None:
    deleted = await delete_medical_center(db, mc_id)
    if not deleted:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Medical center not found")
