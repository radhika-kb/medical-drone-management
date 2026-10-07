"""
core/deps.py
────────────
FastAPI dependency functions shared across multiple routers.

Usage in any route that requires an authenticated user:

    from app.core.deps import get_current_user
    from app.schemas.user import UserInDB

    @router.get("/protected")
    async def protected(current_user: UserInDB = Depends(get_current_user)):
        return {"email": current_user.email}
"""
from fastapi import Depends, HTTPException, status
from fastapi.security import OAuth2PasswordBearer
from motor.motor_asyncio import AsyncIOMotorDatabase

from app.core.security import decode_access_token
from app.db.mongodb import get_database
from app.repositories.user_repository import get_user_by_id
from app.schemas.user import UserInDB

# Tells FastAPI / Swagger where the client should obtain a token.
# tokenUrl must match the login endpoint path (relative to app root).
oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/api/auth/login")


async def get_current_user(
    token: str = Depends(oauth2_scheme),
    db: AsyncIOMotorDatabase = Depends(get_database),  # type: ignore[type-arg]
) -> UserInDB:
    """Decode the Bearer token and return the corresponding user.

    Raises:
        :class:`fastapi.HTTPException` 401 if the token is invalid or the
        user no longer exists in the database.
    """
    payload = decode_access_token(token)          # raises 401 on bad token
    user_id: str = payload["sub"]

    user = await get_user_by_id(db, user_id)
    if user is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="User belonging to this token no longer exists",
            headers={"WWW-Authenticate": "Bearer"},
        )
    return user
