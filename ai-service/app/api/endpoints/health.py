"""
api/endpoints/health.py
───────────────────────
GET /api/health — liveness + readiness probe.

Returns 200 when the application is running and MongoDB is reachable.
Returns 503 when MongoDB is unavailable, so load-balancers / orchestrators
can stop routing traffic to a degraded instance.
"""
from fastapi import APIRouter, status
from fastapi.responses import JSONResponse

from app.core.logging import get_logger
from app.db.mongodb import get_client
from app.schemas.health import HealthResponse

logger = get_logger(__name__)

router = APIRouter(tags=["health"])


@router.get(
    "/health",
    response_model=HealthResponse,
    summary="Health check",
    description=(
        "Returns the operational status of the backend service and its "
        "dependency on MongoDB. Use this endpoint as a liveness/readiness probe."
    ),
    responses={
        200: {"description": "Service is healthy"},
        503: {"description": "Service is degraded — MongoDB unreachable"},
    },
)
async def health_check() -> JSONResponse:
    """Ping MongoDB and report overall service health."""
    client = get_client()
    mongo_ok = False

    if client is not None:
        try:
            await client.admin.command("ping")
            mongo_ok = True
        except Exception as exc:  # noqa: BLE001
            logger.warning("MongoDB ping failed during health check", extra={"error": str(exc)})

    response_body = HealthResponse(
        status="ok" if mongo_ok else "error",
        mongo="connected" if mongo_ok else "disconnected",
    )

    http_status = status.HTTP_200_OK if mongo_ok else status.HTTP_503_SERVICE_UNAVAILABLE

    logger.info(
        "Health check",
        extra={"status": response_body.status, "mongo": response_body.mongo},
    )

    return JSONResponse(content=response_body.model_dump(), status_code=http_status)
