"""
api/endpoints/auth.py
─────────────────────
Authentication endpoints.

POST /api/auth/register  →  create account, return UserResponse (201)
POST /api/auth/login     →  verify credentials, return JWT TokenResponse (200)
"""
from fastapi import APIRouter, Depends, HTTPException, status
from motor.motor_asyncio import AsyncIOMotorDatabase
from pymongo.errors import DuplicateKeyError

from app.core.logging import get_logger
from app.core.security import create_access_token, hash_password, verify_password
from app.db.mongodb import get_database
from app.repositories.user_repository import (
    create_user,
    ensure_indexes,
    get_user_by_email,
)
from app.schemas.auth import LoginRequest, TokenResponse
from app.schemas.user import UserCreate, UserInDB, UserResponse

logger = get_logger(__name__)

router = APIRouter(prefix="/auth", tags=["auth"])


# ── POST /api/auth/register ───────────────────────────────────────────────────

@router.post(
    "/register",
    response_model=UserResponse,
    status_code=status.HTTP_201_CREATED,
    summary="Register a new user",
    description=(
        "Create a new platform user. The password is bcrypt-hashed before "
        "storage and is **never** returned in the response."
    ),
)
async def register(
    payload: UserCreate,
    db: AsyncIOMotorDatabase = Depends(get_database),  # type: ignore[type-arg]
) -> UserResponse:
    # Ensure unique index exists (idempotent)
    await ensure_indexes(db)

    user_in_db = UserInDB(
        name=payload.name,
        email=payload.email,
        password_hash=hash_password(payload.password),
        role=payload.role,
    )

    try:
        created = await create_user(db, user_in_db)
    except DuplicateKeyError:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=f"A user with email '{payload.email}' already exists.",
        )

    logger.info("New user registered", extra={"email": payload.email, "role": payload.role})
    return UserResponse.from_db(created)


# ── POST /api/auth/login ──────────────────────────────────────────────────────

@router.post(
    "/login",
    response_model=TokenResponse,
    summary="Login and receive a JWT",
    description=(
        "Authenticate with email and password. Returns a signed JWT Bearer "
        "token valid for the configured token lifetime (default: 60 minutes)."
    ),
)
async def login(
    payload: LoginRequest,
    db: AsyncIOMotorDatabase = Depends(get_database),  # type: ignore[type-arg]
) -> TokenResponse:
    user = await get_user_by_email(db, payload.email)

    # Use a constant-time failure message — don't reveal whether the email exists
    _invalid = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Incorrect email or password",
        headers={"WWW-Authenticate": "Bearer"},
    )

    if user is None or not verify_password(payload.password, user.password_hash):
        raise _invalid

    token = create_access_token(subject=user.id or "", role=user.role)

    logger.info("User logged in", extra={"email": payload.email, "role": user.role})
    return TokenResponse(access_token=token, user=UserResponse.from_db(user))
