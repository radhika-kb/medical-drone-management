"""
api/endpoints/medical_requests.py
──────────────────────────────────
Medical delivery request endpoints. All routes require a valid JWT.

POST /api/requests         →  create a new request (201)
GET  /api/requests         →  list requests with optional filters (200)
GET  /api/requests/{id}    →  get a single request by id (200 / 404)
"""
from typing import List, Optional

from fastapi import APIRouter, Depends, HTTPException, Query, status, BackgroundTasks
from motor.motor_asyncio import AsyncIOMotorDatabase

from app.core.deps import get_current_user
from app.core.logging import get_logger
from app.db.mongodb import get_database
from app.repositories.medical_center_repository import get_medical_center
from app.repositories.medical_request_repository import (
    create_request,
    get_request,
    list_requests,
)
from app.schemas.medical_request import (
    MedicalRequestCreate,
    MedicalRequestResponse,
    RequestStatus,
    Urgency,
)
from app.schemas.user import UserInDB

logger = get_logger(__name__)

router = APIRouter(prefix="/requests", tags=["medical-requests"])

_auth = Depends(get_current_user)


# ── POST /api/requests ────────────────────────────────────────────────────────

@router.post(
    "",
    response_model=MedicalRequestResponse,
    status_code=status.HTTP_201_CREATED,
    summary="Create a medical delivery request",
    description=(
        "Creates a new delivery request with status **PENDING**. "
        "The `center_id` must reference an existing medical center."
    ),
)
async def create(
    payload: MedicalRequestCreate,
    db: AsyncIOMotorDatabase = Depends(get_database),  # type: ignore[type-arg]
    _: UserInDB = _auth,
) -> MedicalRequestResponse:
    # Validate that the referenced medical center exists
    center = await get_medical_center(db, payload.center_id)
    if center is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Medical center '{payload.center_id}' not found.",
        )

    request = await create_request(db, payload)
    logger.info(
        "Request created via API",
        extra={"request_id": request.request_id, "urgency": request.urgency},
    )
    return request


# ── GET /api/requests ─────────────────────────────────────────────────────────

@router.get(
    "",
    response_model=List[MedicalRequestResponse],
    summary="List medical delivery requests",
    description="Returns requests filtered by optional query parameters, newest first.",
)
async def list_all(
    req_status: Optional[RequestStatus] = Query(
        default=None, alias="status", description="Filter by request status"
    ),
    center_id: Optional[str] = Query(default=None, description="Filter by medical center ID"),
    urgency: Optional[Urgency] = Query(default=None, description="Filter by urgency level"),
    skip: int = Query(default=0, ge=0),
    limit: int = Query(default=20, ge=1, le=100),
    db: AsyncIOMotorDatabase = Depends(get_database),  # type: ignore[type-arg]
    _: UserInDB = _auth,
) -> List[MedicalRequestResponse]:
    return await list_requests(
        db,
        status=req_status,
        center_id=center_id,
        urgency=urgency,
        skip=skip,
        limit=limit,
    )


# ── GET /api/requests/{id} ────────────────────────────────────────────────────

@router.get(
    "/{request_id}",
    response_model=MedicalRequestResponse,
    summary="Get a medical delivery request by ID",
)
async def get_one(
    request_id: str,
    db: AsyncIOMotorDatabase = Depends(get_database),  # type: ignore[type-arg]
    _: UserInDB = _auth,
) -> MedicalRequestResponse:
    req = await get_request(db, request_id)
    if req is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Request not found.",
        )
    return req

# ── POST /api/requests/{id}/dispatch ──────────────────────────────────────────

@router.post(
    "/{request_id}/dispatch",
    summary="Run AI Agent Pipeline for dispatch (Phase 10: LangGraph)",
)
async def dispatch_request(
    request_id: str,
    background_tasks: BackgroundTasks,
    db: AsyncIOMotorDatabase = Depends(get_database),  # type: ignore[type-arg]
    _: UserInDB = _auth,
) -> dict:
    req = await get_request(db, request_id)
    if req is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Request not found.",
        )

    # ── Phase 10: LangGraph orchestration ─────────────────────────────────────
    from app.orchestration.graph import dispatch_graph
    from app.orchestration.explainer import generate_explanation

    # Build the initial state — nodes write their own slices from here
    initial_state = {
        "request": req,
        "db": db,
        "priority_result": None,
        "weather_result": None,
        "allocation_result": None,
        "selected_drone": None,
        "route_result": None,
        "safety_result": None,
        "mission": None,
        "halt_reason": None,
        "final_status": None,
        "decision_trail": [],
        "llm_explanation": None,
    }

    # Run the deterministic graph
    final_state = await dispatch_graph.ainvoke(initial_state)

    # ── Post-graph: read-only LLM explanation (never blocks dispatch) ──────────
    explanation = await generate_explanation(final_state)
    # Note: we do NOT write explanation back into final_state — it belongs
    # only in the response so the structured decision_trail stays pristine.

    # ── Start simulation background task if mission was created ───────────────
    if final_state.get("final_status") == "MISSION_CREATED" and final_state.get("mission"):
        from app.simulation.engine import run_mission_simulation
        background_tasks.add_task(
            run_mission_simulation,
            final_state["mission"].mission_id,
            db,
        )

    # ── Build response (same shape as pre-refactor for zero frontend diff) ────
    status_str      = final_state.get("final_status", "UNKNOWN")
    priority_result = final_state.get("priority_result")
    weather_result  = final_state.get("weather_result")
    alloc_result    = final_state.get("allocation_result")
    route_result    = final_state.get("route_result")
    safety_result   = final_state.get("safety_result")
    mission         = final_state.get("mission")
    halt_reason     = final_state.get("halt_reason")
    decision_trail  = final_state.get("decision_trail", [])

    # Default message
    if status_str == "MISSION_CREATED":
        message = "Dispatch pipeline complete. Mission created successfully."
    elif halt_reason:
        message = halt_reason
    else:
        message = f"Dispatch ended with status: {status_str}"

    return {
        "status":           status_str,
        "priority_agent":   priority_result,
        "weather_agent":    weather_result,
        "allocation_agent": alloc_result,
        "route_agent":      route_result,
        "safety_agent":     safety_result,
        "mission":          mission,
        "message":          message,
        # Phase 10 additions (additive — old clients ignore unknown keys)
        "decision_trail":   decision_trail,
        "llm_explanation":  explanation,
    }
