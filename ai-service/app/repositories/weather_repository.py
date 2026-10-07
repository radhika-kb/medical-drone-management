from motor.motor_asyncio import AsyncIOMotorDatabase
from app.schemas.weather import WeatherObservationCreate, WeatherObservation

async def save_weather_observation(db: AsyncIOMotorDatabase, obs_in: WeatherObservationCreate) -> WeatherObservation:
    """Save a new weather observation into the weather_observations collection."""
    obs_dict = obs_in.model_dump()
    result = await db["weather_observations"].insert_one(obs_dict)
    
    return WeatherObservation(
        id=str(result.inserted_id),
        **obs_dict
    )
