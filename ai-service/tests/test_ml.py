import pytest
from pydantic import ValidationError
from ml.schemas import WeatherFeaturesInput

def test_weather_features_input_schema_rejects_leakage():
    """
    Test that the input schema specifically rejects target leakage variables.
    This acts as a guardrail against someone accidentally including targets in the features.
    """
    base_input = {
        "temperature_c": 25.0,
        "humidity_pct": 60.0,
        "precipitation_mm": 0.0,
        "wind_speed_kmh": 15.0,
        "wind_gust_kmh": 20.0,
        "wind_direction_deg": 180,
        "visibility_km": 10.0,
        "pressure_hpa": 1012.0,
        "cloud_cover_pct": 20.0,
        "dew_point_c": 18.0,
        "uv_index": 5.0,
        "hour": 12,
        "day_of_week": 2,
        "month": 8,
        "weather_condition": "Clear",
        "rain_event": 0
    }

    # Should succeed without leakage variables
    WeatherFeaturesInput(**base_input)

    # Test rejection of flight_condition
    with pytest.raises(ValidationError) as exc_info:
        leakage_input = base_input.copy()
        leakage_input["flight_condition"] = "SAFE"
        WeatherFeaturesInput(**leakage_input)
    assert "Extra inputs are not permitted" in str(exc_info.value)

    # Test rejection of flight_risk_score
    with pytest.raises(ValidationError) as exc_info:
        leakage_input = base_input.copy()
        leakage_input["flight_risk_score"] = 90.0
        WeatherFeaturesInput(**leakage_input)
    assert "Extra inputs are not permitted" in str(exc_info.value)

    # Test rejection of prediction_confidence
    with pytest.raises(ValidationError) as exc_info:
        leakage_input = base_input.copy()
        leakage_input["prediction_confidence"] = 0.99
        WeatherFeaturesInput(**leakage_input)
    assert "Extra inputs are not permitted" in str(exc_info.value)
