"""
api/router.py
─────────────
Top-level API router that aggregates all versioned sub-routers.

Each feature module registers its own APIRouter in api/endpoints/ and is
imported + included here. This keeps main.py clean and makes it trivial to
add new route groups in later phases.
"""
from fastapi import APIRouter

from app.api.endpoints import auth as auth_endpoint
from app.api.endpoints import drones as drones_endpoint
from app.api.endpoints import health as health_endpoint
from app.api.endpoints import medical_centers as medical_centers_endpoint
from app.api.endpoints import medical_requests as medical_requests_endpoint
from app.api.endpoints import weather as weather_endpoint
from app.api.endpoints import missions as missions_endpoint
from app.api.endpoints import ws as ws_endpoint

# Root router — all routes under this router will be prefixed with /api
# (the prefix is applied in main.py via app.include_router).
api_router = APIRouter()

api_router.include_router(health_endpoint.router)
api_router.include_router(auth_endpoint.router)
api_router.include_router(medical_centers_endpoint.router)
api_router.include_router(medical_requests_endpoint.router)
api_router.include_router(drones_endpoint.router)
api_router.include_router(missions_endpoint.router)
api_router.include_router(ws_endpoint.router)
api_router.include_router(weather_endpoint.router, prefix="/weather", tags=["weather"])
