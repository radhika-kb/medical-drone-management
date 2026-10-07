from datetime import datetime
from typing import Dict, Any, Tuple
from motor.motor_asyncio import AsyncIOMotorDatabase
from app.services.weather_client import fetch_current_weather
from app.repositories.weather_repository import save_weather_observation
from app.schemas.weather import WeatherObservationCreate
from app.core.logging import get_logger
from ml.predict import predict_flight_condition
from ml.schemas import WeatherFeaturesInput

logger = get_logger(__name__)

async def assess_flight_weather(lat: float, lng: float, db: AsyncIOMotorDatabase) -> Dict[str, Any]:
    """
    Main Weather Agent logic.
    1. Fetches current model-derived weather conditions.
    2. Uses fail-closed logic if weather API fails.
    3. Formats data for ML model and gets prediction.
    4. Persists the observation.
    
    Returns:
        dict: { "flight_condition", "probability", "raw_weather_snapshot", "error" }
    """
    # 1. Fetch live weather
    success, raw_data, error_reason = await fetch_current_weather(lat, lng)
    
    # 2. Handle failure (Fail-Closed)
    if not success:
        logger.error(f"Weather Agent fail-closed due to API error: {error_reason}")
        
        # Save fail-closed observation
        obs = WeatherObservationCreate(
            latitude=lat,
            longitude=lng,
            raw_snapshot=raw_data,
            flight_condition="UNSAFE",
            probability=1.0,
            error_reason=error_reason
        )
        await save_weather_observation(db, obs)
        
        return {
            "flight_condition": "UNSAFE",
            "probability": 1.0,
            "raw_weather_snapshot": raw_data,
            "error": error_reason
        }
        
    # 3. Success -> Extract and Map Features
    try:
        current = raw_data["current"]
        
        # Open-Meteo returns the observation timestamp in the requested local
        # timezone (Asia/Kolkata), so use its wall-clock components directly.
        dt = datetime.fromisoformat(current["time"])

        precip_mm = float(current["precip_mm"])

        temp_c = float(current["temp_c"])
        humidity_pct = float(current["humidity"])
        dew_point_c = float(current["dewpoint_c"])

        condition_text = current["condition"]["text"]
        
        # Build strictly enforced schema for ML
        ml_input = WeatherFeaturesInput(
            temperature_c=temp_c,
            humidity_pct=humidity_pct,
            precipitation_mm=precip_mm,
            wind_speed_kmh=float(current["wind_kph"]),
            wind_gust_kmh=float(current["gust_kph"]),
            wind_direction_deg=int(current["wind_degree"]),
            visibility_km=float(current["vis_km"]),
            pressure_hpa=float(current["pressure_mb"]),
            cloud_cover_pct=float(current["cloud"]),
            dew_point_c=dew_point_c,
            uv_index=float(current["uv"]),
            hour=dt.hour,
            day_of_week=dt.weekday(),
            month=dt.month,
            weather_condition=condition_text,
            rain_event=1 if precip_mm > 0 else 0
        )
        
        # Run ML inference
        ml_result = predict_flight_condition(ml_input)
        flight_condition = ml_result["flight_condition"]
        probability = ml_result["probability"]
        
    except Exception as e:
        logger.error(f"Weather Agent failed during feature mapping or ML prediction: {str(e)}")
        # Fail-closed
        obs = WeatherObservationCreate(
            latitude=lat,
            longitude=lng,
            raw_snapshot=raw_data,
            flight_condition="UNSAFE",
            probability=1.0,
            error_reason=f"Processing Error: {str(e)}"
        )
        await save_weather_observation(db, obs)
        
        return {
            "flight_condition": "UNSAFE",
            "probability": 1.0,
            "raw_weather_snapshot": raw_data,
            "error": f"Processing Error: {str(e)}"
        }

    # 4. Success -> Save to database
    obs = WeatherObservationCreate(
        latitude=lat,
        longitude=lng,
        raw_snapshot=raw_data,
        flight_condition=flight_condition,
        probability=probability,
        error_reason=None
    )
    await save_weather_observation(db, obs)

    return {
        "flight_condition": flight_condition,
        "probability": probability,
        "raw_weather_snapshot": raw_data,
        "error": None
    }
