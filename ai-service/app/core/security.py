"""
core/security.py
────────────────
Password hashing and JWT token utilities.

All crypto operations are isolated here so the rest of the codebase
stays free of auth details. Import these helpers, never roll your own.

Password hashing
----------------
    from app.core.security import hash_password, verify_password

    hashed = hash_password("plaintext")          # store this in DB
    ok = verify_password("plaintext", hashed)    # True / False

JWT tokens
----------
    from app.core.security import create_access_token, decode_access_token

    token = create_access_token(subject=str(user_id), role=user.role)
    payload = decode_access_token(token)         # raises 401 on failure
    user_id = payload["sub"]
    role     = payload["role"]
"""
from __future__ import annotations

from datetime import datetime, timedelta, timezone
from typing import Any

import bcrypt
import jwt
from fastapi import HTTPException, status

from app.core.config import get_settings

# ── Password hashing ──────────────────────────────────────────────────────────


def hash_password(plain: str) -> str:
    """Return a bcrypt hash of *plain*.  Never store the plaintext."""
    salt = bcrypt.gensalt()
    return bcrypt.hashpw(plain.encode("utf-8"), salt).decode("utf-8")


def verify_password(plain: str, hashed: str) -> bool:
    """Return ``True`` if *plain* matches *hashed*, ``False`` otherwise."""
    try:
        return bcrypt.checkpw(plain.encode("utf-8"), hashed.encode("utf-8"))
    except Exception:  # noqa: BLE001
        return False


# ── JWT tokens ────────────────────────────────────────────────────────────────

_ALGORITHM = "HS256"


def create_access_token(
    subject: str,
    role: str,
    expires_delta: timedelta | None = None,
) -> str:
    """Sign and return a JWT access token.

    Args:
        subject: Typically the string representation of the user's ``_id``.
        role: User role string stored as a claim (e.g. ``"hospital_admin"``).
        expires_delta: Token lifetime; defaults to
            ``settings.jwt_access_token_expire_minutes``.

    Returns:
        A signed JWT string ready to send to the client.
    """
    settings = get_settings()
    if expires_delta is None:
        expires_delta = timedelta(minutes=settings.jwt_access_token_expire_minutes)

    now = datetime.now(tz=timezone.utc)
    payload: dict[str, Any] = {
        "sub": subject,
        "role": role,
        "iat": now,
        "exp": now + expires_delta,
    }
    return jwt.encode(payload, settings.jwt_secret, algorithm=_ALGORITHM)


def decode_access_token(token: str) -> dict[str, Any]:
    """Decode and validate a JWT access token.

    Args:
        token: Raw JWT string from the ``Authorization: Bearer <token>`` header.

    Returns:
        The decoded payload dictionary (contains ``sub``, ``role``, ``exp`` …).

    Raises:
        :class:`fastapi.HTTPException` 401 if the token is expired, tampered,
        or otherwise invalid.
    """
    settings = get_settings()
    credentials_exception = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Could not validate credentials",
        headers={"WWW-Authenticate": "Bearer"},
    )
    try:
        payload: dict[str, Any] = jwt.decode(
            token,
            settings.jwt_secret,
            algorithms=[_ALGORITHM],
        )
        if payload.get("sub") is None:
            raise credentials_exception
        return payload
    except jwt.ExpiredSignatureError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Token has expired",
            headers={"WWW-Authenticate": "Bearer"},
        )
    except jwt.PyJWTError:
        raise credentials_exception
