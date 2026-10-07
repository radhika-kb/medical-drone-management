from datetime import datetime, timezone
from typing import List, Optional
from pydantic import BaseModel, Field

class Coordinate(BaseModel):
    lat: float
    lng: float

class MissionCreate(BaseModel):
    request_id: str
    drone_id: str
    route: List[Coordinate]
    distance_km: float
    eta_minutes: float
    battery_required_pct: float

class MissionResponse(MissionCreate):
    id: str
    mission_id: str
    status: str = "ASSIGNED"
    started_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))

    model_config = {
        "populate_by_name": True,
    }
