"""
schemas/user.py
───────────────
Pydantic models for the User domain.

Three strictly separated models prevent password_hash from ever reaching
the client response:

    UserCreate   →  input from the registration request body
    UserInDB     →  internal representation that includes password_hash
                    (loaded from / saved to MongoDB)
    UserResponse →  sent back to the client — NEVER contains password_hash
"""
from __future__ import annotations

from datetime import datetime, timezone
from typing import Literal, Optional

from pydantic import BaseModel, EmailStr, Field


# ── Role constraint ───────────────────────────────────────────────────────────

UserRole = Literal["hospital_admin", "system_admin"]


# ── Input model ───────────────────────────────────────────────────────────────

class UserCreate(BaseModel):
    """Fields accepted from the client when registering a new user."""

    name: str = Field(..., min_length=2, max_length=100, examples=["Dr. Priya Sharma"])
    email: EmailStr = Field(..., examples=["priya@cityhospital.com"])
    password: str = Field(..., min_length=8, examples=["supersecret"])
    role: UserRole = Field(default="hospital_admin")


# ── DB model ──────────────────────────────────────────────────────────────────

class UserInDB(BaseModel):
    """Full internal user record, including the bcrypt hash.

    ``id`` maps to MongoDB's ``_id`` (stored/retrieved as a plain string).
    This model is NEVER serialised back to HTTP responses directly.
    """

    id: Optional[str] = Field(default=None, alias="_id")
    name: str
    email: EmailStr
    password_hash: str
    role: UserRole = "hospital_admin"
    created_at: datetime = Field(default_factory=lambda: datetime.now(tz=timezone.utc))

    model_config = {
        "populate_by_name": True,   # allow both 'id' and '_id'
        "arbitrary_types_allowed": True,
    }

    def to_mongo(self) -> dict:
        """Serialise to a dict suitable for inserting into MongoDB.

        Excludes ``id`` (``_id`` is assigned by Mongo on insert).
        """
        data = self.model_dump(by_alias=False, exclude={"id"})
        return data


# ── Response model ────────────────────────────────────────────────────────────

class UserResponse(BaseModel):
    """Public representation of a user — safe to return in API responses.

    ``password_hash`` is intentionally absent.
    """

    id: str
    name: str
    email: EmailStr
    role: UserRole
    created_at: datetime

    model_config = {
        "json_schema_extra": {
            "examples": [
                {
                    "id": "66f1a2b3c4d5e6f7a8b9c0d1",
                    "name": "Dr. Priya Sharma",
                    "email": "priya@cityhospital.com",
                    "role": "hospital_admin",
                    "created_at": "2026-08-31T06:00:00Z",
                }
            ]
        }
    }

    @classmethod
    def from_db(cls, user: UserInDB) -> "UserResponse":
        """Convenience constructor from a ``UserInDB`` instance."""
        return cls(
            id=user.id or "",
            name=user.name,
            email=user.email,
            role=user.role,
            created_at=user.created_at,
        )
