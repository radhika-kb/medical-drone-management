from typing import Dict, Any
from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel

from app.core.deps import get_current_user
from app.db.mongodb import get_database as get_db
from app.schemas.user import UserInDB
from app.agents.weather_agent import assess_flight_weather
from motor.motor_asyncio import AsyncIOMotorDatabase

router = APIRouter()

class WeatherPredictionRequest(BaseModel):
    latitude: float
    longitude: float

class WeatherPredictionResponse(BaseModel):
    flight_condition: str
    probability: float
    raw_weather_snapshot: Dict[str, Any]
    error: str | None = None

@router.post("/predict", response_model=WeatherPredictionResponse)
async def predict_weather(
    request: WeatherPredictionRequest,
    db: AsyncIOMotorDatabase = Depends(get_db),
    current_user: UserInDB = Depends(get_current_user)
):
    """
    Predict flight condition based on real-time weather at the given coordinates.
    This triggers the Weather Agent which:
    1. Fetches current weather from the external API.
    2. Runs it through the ML model.
    3. Persists the observation.
    4. Returns fail-closed UNSAFE if the API is unavailable.
    """
    result = await assess_flight_weather(
        lat=request.latitude,
        lng=request.longitude,
        db=db
    )
    
    return WeatherPredictionResponse(**result)
