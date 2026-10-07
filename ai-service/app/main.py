"""
main.py
───────
FastAPI application entrypoint for the AI-Powered Medical Drone Delivery
Management & Simulation Platform backend.

Startup order (managed by the lifespan context manager):
  1. Settings are validated via pydantic-settings.
  2. MongoDB connection is established and pinged.
  3. CORS middleware is configured from CORS_ORIGINS env var.
  4. All API routers are mounted.

On shutdown the Motor connection pool is closed cleanly.
"""
from contextlib import asynccontextmanager
from typing import AsyncGenerator

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.router import api_router
from app.core.config import get_settings
from app.core.logging import get_logger
from app.db.mongodb import connect_db, disconnect_db

logger = get_logger(__name__)


# ── Lifespan ──────────────────────────────────────────────────────────────────

@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncGenerator[None, None]:
    """Manage application-level resources across the process lifetime."""
    settings = get_settings()

    logger.info(
        "Starting Medical Drone Management backend",
        extra={"version": "0.1.0", "database": settings.mongodb_database},
    )

    await connect_db()

    yield  # ← application is running and handling requests

    logger.info("Shutting down Medical Drone Management backend")
    await disconnect_db()


# ── App factory ───────────────────────────────────────────────────────────────

def create_app() -> FastAPI:
    """Construct and configure the FastAPI application instance."""
    settings = get_settings()

    application = FastAPI(
        title="Medical Drone Management API",
        description=(
            "AI-Powered Medical Drone Delivery Management & Simulation Platform — "
            "Phase 1 backend."
        ),
        version="0.1.0",
        lifespan=lifespan,
    )

    # ── CORS ──────────────────────────────────────────────────────────────────
    application.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origins_list,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    # ── Routes ────────────────────────────────────────────────────────────────
    from app.api.endpoints import ai_decision
    application.include_router(ai_decision.router)
    application.include_router(ai_decision.router, prefix="/api")
    application.include_router(api_router, prefix="/api")

    return application


app = create_app()
