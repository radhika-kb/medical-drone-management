"""
tests/test_auth.py
──────────────────
Unit tests for Phase 2 authentication features.

Tests are fully isolated — no real MongoDB connection required.
Repository calls are mocked with AsyncMock where needed.

Run with:
    cd backend
    python -m pytest tests/test_auth.py -v
"""
from __future__ import annotations

import sys
import os

# Ensure the backend/app package is importable when running from backend/
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from datetime import timedelta
from unittest.mock import AsyncMock, patch

import pytest
from fastapi import HTTPException
from pymongo.errors import DuplicateKeyError

from app.core.security import (
    create_access_token,
    decode_access_token,
    hash_password,
    verify_password,
)


# ═══════════════════════════════════════════════════════════════════════════════
# Password hashing
# ═══════════════════════════════════════════════════════════════════════════════

class TestPasswordHashing:
    def test_hash_is_not_plaintext(self):
        """Stored hash must differ from the original password."""
        plain = "my_secure_password"
        hashed = hash_password(plain)
        assert hashed != plain

    def test_roundtrip_correct_password(self):
        """verify_password returns True when the password matches the hash."""
        plain = "my_secure_password"
        hashed = hash_password(plain)
        assert verify_password(plain, hashed) is True

    def test_roundtrip_wrong_password(self):
        """verify_password returns False for an incorrect password."""
        hashed = hash_password("correct_password")
        assert verify_password("wrong_password", hashed) is False

    def test_two_hashes_differ(self):
        """bcrypt produces a different salt each time — same input, different hash."""
        plain = "same_password"
        assert hash_password(plain) != hash_password(plain)

    def test_empty_password_hashes(self):
        """Empty string password should still hash and verify cleanly."""
        hashed = hash_password("")
        assert verify_password("", hashed) is True
        assert verify_password("not_empty", hashed) is False


# ═══════════════════════════════════════════════════════════════════════════════
# JWT tokens
# ═══════════════════════════════════════════════════════════════════════════════

class TestJWT:
    _USER_ID = "66f1a2b3c4d5e6f7a8b9c0d1"
    _ROLE = "hospital_admin"

    def test_valid_token_decodes(self):
        """A freshly issued token should decode successfully."""
        token = create_access_token(subject=self._USER_ID, role=self._ROLE)
        payload = decode_access_token(token)

        assert payload["sub"] == self._USER_ID
        assert payload["role"] == self._ROLE

    def test_expired_token_raises_401(self):
        """A token whose expiry is in the past must raise HTTPException 401."""
        token = create_access_token(
            subject=self._USER_ID,
            role=self._ROLE,
            expires_delta=timedelta(seconds=-1),   # already expired
        )
        with pytest.raises(HTTPException) as exc_info:
            decode_access_token(token)

        assert exc_info.value.status_code == 401
        assert "expired" in exc_info.value.detail.lower()

    def test_tampered_token_raises_401(self):
        """Mutating the JWT signature must raise HTTPException 401."""
        token = create_access_token(subject=self._USER_ID, role=self._ROLE)

        # Corrupt the signature part (everything after the last '.')
        # Replace the entire signature with garbage so it is reliably invalid.
        header_payload, _, _ = token.rsplit(".", 2)
        tampered = f"{header_payload}.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

        with pytest.raises(HTTPException) as exc_info:
            decode_access_token(tampered)

        assert exc_info.value.status_code == 401

    def test_garbage_token_raises_401(self):
        """A completely invalid string must raise HTTPException 401."""
        with pytest.raises(HTTPException) as exc_info:
            decode_access_token("not.a.jwt")

        assert exc_info.value.status_code == 401

    def test_token_missing_sub_raises_401(self):
        """A token without a 'sub' claim must raise HTTPException 401."""
        import jwt as pyjwt
        from app.core.config import get_settings
        from datetime import datetime, timezone

        settings = get_settings()
        # Craft a token with no 'sub'
        payload = {
            "role": self._ROLE,
            "exp": datetime.now(tz=timezone.utc) + timedelta(minutes=60),
        }
        no_sub_token = pyjwt.encode(payload, settings.jwt_secret, algorithm="HS256")

        with pytest.raises(HTTPException) as exc_info:
            decode_access_token(no_sub_token)

        assert exc_info.value.status_code == 401


# ═══════════════════════════════════════════════════════════════════════════════
# Register endpoint — duplicate email
# ═══════════════════════════════════════════════════════════════════════════════

class TestRegisterEndpoint:
    """Tests for the register route using mocked repository calls."""

    @pytest.mark.asyncio
    async def test_register_duplicate_email_returns_409(self):
        """Registering the same email twice must raise HTTP 409 Conflict."""
        from app.api.endpoints.auth import register
        from app.schemas.user import UserCreate

        payload = UserCreate(
            name="Test User",
            email="dup@example.com",
            password="password123",
        )

        # Patch at the location where the name is looked up (the endpoint module)
        with patch(
            "app.api.endpoints.auth.ensure_indexes", new_callable=AsyncMock
        ) as mock_index, patch(
            "app.api.endpoints.auth.create_user",
            new_callable=AsyncMock,
            side_effect=DuplicateKeyError("duplicate key"),
        ):
            mock_index.return_value = None
            mock_db = AsyncMock()

            with pytest.raises(HTTPException) as exc_info:
                await register(payload=payload, db=mock_db)

        assert exc_info.value.status_code == 409
        assert "already exists" in exc_info.value.detail

    @pytest.mark.asyncio
    async def test_register_success_excludes_password_hash(self):
        """A successful registration response must not contain password_hash."""
        from app.api.endpoints.auth import register
        from app.schemas.user import UserCreate, UserInDB, UserResponse

        payload = UserCreate(
            name="New User",
            email="newuser@example.com",
            password="strongpass1",
        )

        fake_user = UserInDB(
            _id="abc123",
            name=payload.name,
            email=payload.email,
            password_hash=hash_password(payload.password),
            role="hospital_admin",
        )

        with patch(
            "app.api.endpoints.auth.ensure_indexes", new_callable=AsyncMock
        ), patch(
            "app.api.endpoints.auth.create_user",
            new_callable=AsyncMock,
            return_value=fake_user,
        ):
            mock_db = AsyncMock()
            response: UserResponse = await register(payload=payload, db=mock_db)

        assert response.email == payload.email
        # UserResponse is a Pydantic model — confirm no password_hash field
        assert not hasattr(response, "password_hash")
        response_dict = response.model_dump()
        assert "password_hash" not in response_dict
