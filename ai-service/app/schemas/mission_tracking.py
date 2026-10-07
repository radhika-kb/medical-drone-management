from datetime import datetime, timezone
from pydantic import BaseModel, Field

class MissionTrackingCreate(BaseModel):
    mission_id: str
    lat: float
    lng: float
    battery_pct: float
    remaining_distance_km: float
    eta_minutes: float
    status: str
    timestamp: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))

class MissionTrackingResponse(MissionTrackingCreate):
    id: str = Field(..., alias="_id")

    model_config = {
        "populate_by_name": True,
    }
