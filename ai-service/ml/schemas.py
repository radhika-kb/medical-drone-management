from pydantic import BaseModel, Field, ConfigDict

class WeatherFeaturesInput(BaseModel):
    """
    Schema for raw weather API input.
    CRITICAL: This schema MUST NOT include target leakage variables like 
    flight_condition, flight_risk_score, or prediction_confidence.
    """
    temperature_c: float = Field(..., description="Temperature in Celsius")
    humidity_pct: float = Field(..., description="Humidity percentage")
    precipitation_mm: float = Field(..., description="Precipitation in mm")
    wind_speed_kmh: float = Field(..., description="Wind speed in km/h")
    wind_gust_kmh: float = Field(..., description="Wind gust in km/h")
    wind_direction_deg: int = Field(..., description="Wind direction in degrees")
    visibility_km: float = Field(..., description="Visibility in km")
    pressure_hpa: float = Field(..., description="Atmospheric pressure in hPa")
    cloud_cover_pct: float = Field(..., description="Cloud cover percentage")
    dew_point_c: float = Field(..., description="Dew point in Celsius")
    uv_index: float = Field(..., description="UV index")
    hour: int = Field(..., description="Hour of the day (0-23)")
    day_of_week: int = Field(..., description="Day of the week (0-6)")
    month: int = Field(..., description="Month of the year (1-12)")
    weather_condition: str = Field(..., description="General weather condition categorical (e.g. Clear, Heavy Rain)")
    rain_event: int = Field(..., description="Binary flag indicating a rain event (0 or 1)")

    # Pydantic v2: explicitly forbid extra fields to strictly prevent target leakage in the input
    model_config = ConfigDict(extra="forbid")
