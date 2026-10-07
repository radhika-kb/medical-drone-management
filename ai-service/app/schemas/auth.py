"""
schemas/auth.py
───────────────
Request / response models for authentication endpoints.
"""
from pydantic import BaseModel, EmailStr, Field

from app.schemas.user import UserResponse


class LoginRequest(BaseModel):
    """Credentials submitted to POST /api/auth/login."""

    email: EmailStr = Field(..., examples=["priya@cityhospital.com"])
    password: str = Field(..., examples=["supersecret"])


class TokenResponse(BaseModel):
    """Returned on successful login."""

    access_token: str = Field(..., description="Signed JWT Bearer token")
    token_type: str = Field(default="bearer")
    user: UserResponse = Field(..., description="Public profile of the authenticated user")

    model_config = {
        "json_schema_extra": {
            "examples": [
                {
                    "access_token": "<jwt-string>",
                    "token_type": "bearer",
                    "user": {
                        "id": "66f1a2b3c4d5e6f7a8b9c0d1",
                        "name": "Dr. Priya Sharma",
                        "email": "priya@cityhospital.com",
                        "role": "hospital_admin",
                        "created_at": "2026-08-31T06:00:00Z",
                    },
                }
            ]
        }
    }
