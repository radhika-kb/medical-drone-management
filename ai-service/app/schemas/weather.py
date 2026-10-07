from datetime import datetime
from typing import Dict, Any, Optional
from pydantic import BaseModel, Field

class WeatherObservationCreate(BaseModel):
    """Data required to save a new weather observation."""
    latitude: float
    longitude: float
    timestamp: datetime = Field(default_factory=datetime.utcnow)
    raw_snapshot: Optional[Dict[str, Any]] = None
    flight_condition: str = Field(..., description="SAFE, CAUTION, or UNSAFE")
    probability: float = Field(..., description="Probability score from ML model")
    error_reason: Optional[str] = None

class WeatherObservation(WeatherObservationCreate):
    """Complete WeatherObservation representation from the DB."""
    id: str
