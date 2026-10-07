import httpx
import os
from typing import Dict, Any, Tuple
from app.core.logging import get_logger

logger = get_logger(__name__)

WEATHER_API_BASE_URL = os.getenv("WEATHER_API_BASE_URL", "https://api.open-meteo.com/v1/forecast")
WEATHER_API_TIMEOUT_SECONDS = float(os.getenv("WEATHER_API_TIMEOUT_SECONDS", "5"))

async def fetch_current_weather(lat: float, lng: float) -> Tuple[bool, Dict[str, Any], str]:
    """
    Fetch current model-derived Bengaluru-area weather from Open-Meteo and
    normalize its returned values to the existing Weather Agent input shape.
    
    Returns:
        Tuple of (success_boolean, raw_data_dict, error_reason_string)
    """
    url = WEATHER_API_BASE_URL
    params = {
        "latitude": lat,
        "longitude": lng,
        "current": ",".join((
            "temperature_2m", "relative_humidity_2m", "precipitation", "weather_code",
            "pressure_msl", "surface_pressure", "cloud_cover", "dew_point_2m",
            "wind_speed_10m", "wind_direction_10m", "wind_gusts_10m", "visibility", "uv_index",
        )),
        "timezone": "Asia/Kolkata",
        "wind_speed_unit": "kmh",
    }
    
    try:
        async with httpx.AsyncClient(timeout=WEATHER_API_TIMEOUT_SECONDS) as client:
            response = await client.get(url, params=params)
            response.raise_for_status()
            
            payload = response.json()
            current = payload.get("current") or {}
            required_fields = (
                "time", "temperature_2m", "relative_humidity_2m", "precipitation", "weather_code",
                "pressure_msl", "cloud_cover", "dew_point_2m", "wind_speed_10m",
                "wind_direction_10m", "wind_gusts_10m", "visibility", "uv_index",
            )
            missing = [field for field in required_fields if current.get(field) is None]
            if missing:
                return False, payload, f"Weather response missing required observations: {', '.join(missing)}"

            weather_code = int(current["weather_code"])
            condition = _weather_code_description(weather_code)
            return True, {
                "location": {"name": "Bengaluru", "region": "Karnataka", "country": "India"},
                "current": {
                    "time": current["time"],
                    "temp_c": current["temperature_2m"],
                    "humidity": current["relative_humidity_2m"],
                    "precip_mm": current["precipitation"],
                    "wind_kph": current["wind_speed_10m"],
                    "gust_kph": current["wind_gusts_10m"],
                    "wind_degree": current["wind_direction_10m"],
                    "vis_km": current["visibility"] / 1000.0,
                    "pressure_mb": current.get("surface_pressure") or current["pressure_msl"],
                    "cloud": current["cloud_cover"],
                    "dewpoint_c": current["dew_point_2m"],
                    "uv": current["uv_index"],
                    "condition": {"text": condition},
                    "weather_code": weather_code,
                },
                "source": "Open-Meteo",
                "source_url": "https://open-meteo.com/",
                "timezone": payload.get("timezone", "Asia/Kolkata"),
            }, ""
            
    except httpx.TimeoutException:
        logger.warning(f"Weather API timeout for {lat},{lng}")
        return False, {}, "API Timeout"
    except httpx.HTTPStatusError as e:
        logger.warning(f"Weather API returned {e.response.status_code} for {lat},{lng}")
        return False, {}, f"API returned {e.response.status_code}"
    except Exception as e:
        logger.error(f"Weather API request failed: {str(e)}")
        return False, {}, f"API Request Failed: {str(e)}"


def _weather_code_description(code: int) -> str:
    """Translate WMO present-weather codes returned by Open-Meteo."""
    if code == 0:
        return "Clear sky"
    if code in (1, 2, 3):
        return "Mainly clear" if code == 1 else "Partly cloudy" if code == 2 else "Overcast"
    if code in (45, 48):
        return "Fog"
    if code in (51, 53, 55):
        return "Drizzle"
    if code in (56, 57):
        return "Freezing drizzle"
    if code in (61, 63, 65):
        return "Rain"
    if code in (66, 67):
        return "Freezing rain"
    if code in (71, 73, 75, 77):
        return "Snow"
    if code in (80, 81, 82):
        return "Rain showers"
    if code in (85, 86):
        return "Snow showers"
    if code == 95:
        return "Thunderstorm"
    if code in (96, 99):
        return "Thunderstorm with hail"
    return f"WMO weather code {code}"
