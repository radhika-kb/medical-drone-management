from __future__ import annotations

import pytest

from app.api.endpoints.ai_decision import AIDecisionRequest, GeoPoint, run_weather_agent
from app.orchestration.graph import dispatch_graph


def weather_response(temp: float, wind: float, timestamp: str) -> dict:
    return {
        "source": "Open-Meteo",
        "current": {
            "time": timestamp,
            "temp_c": temp,
            "humidity": 62,
            "precip_mm": 0.2,
            "wind_kph": wind,
            "gust_kph": wind + 4,
            "wind_degree": 135,
            "vis_km": 8.4,
            "pressure_mb": 1008.2,
            "cloud": 35,
            "dewpoint_c": 18.5,
            "uv": 4.0,
            "condition": {"text": "Light rain"},
            "weather_code": 61,
        },
    }


@pytest.mark.asyncio
async def test_active_graph_uses_live_weather_features_for_eta_and_safety(monkeypatch):
    pickup = GeoPoint(lat=12.9716, lng=77.5946)
    destination = GeoPoint(lat=12.9800, lng=77.6100)
    live = {
        (pickup.lat, pickup.lng): weather_response(29.1, 11.2, "2026-10-06T14:00"),
        (destination.lat, destination.lng): weather_response(30.4, 17.8, "2026-10-06T14:00"),
    }

    async def fetch(lat: float, lng: float):
        return True, live[(lat, lng)], ""

    monkeypatch.setattr("app.services.weather_client.fetch_current_weather", fetch)

    weather = await run_weather_agent(pickup, destination)
    assert len(weather["observations"]) == 2
    assert weather["flight_condition"] in {"SAFE", "CAUTION", "UNSAFE"}
    assert weather["observations"][0]["temperature_c"] == 29.1
    assert weather["observations"][1]["wind_speed_kmh"] == 17.8
    assert all(item["model_name"] for item in weather["observations"])
    assert all(item["model_prediction"] in {"SAFE", "CAUTION", "UNSAFE"}
               for item in weather["observations"])
    assert all(0.0 <= item["confidence"] <= 1.0 for item in weather["observations"])
    assert weather["reason"] and "Open-Meteo" in weather["reason"]

    observed = {}
    monkeypatch.setattr("app.api.endpoints.ai_decision.run_priority_agent",
                        lambda *args: {"priority_level": "HIGH", "priority_score": 80, "reason": "test"})
    monkeypatch.setattr("app.api.endpoints.ai_decision.run_supply_match_agent",
                        lambda *args: {"status": "MATCH_FOUND", "reason": "test"})
    monkeypatch.setattr("app.api.endpoints.ai_decision.run_allocation_agent",
                        lambda *args: {"decision": "ALLOCATED", "drone_code": "DR-TEST",
                                       "drone_lat": pickup.lat, "drone_lng": pickup.lng,
                                       "score": 1.0, "reason": "test"})
    monkeypatch.setattr("app.api.endpoints.ai_decision.run_route_agent",
                        lambda *args: {"status": "SAFE", "algorithm": "test", "reason": "test",
                                       "distance_km": 3.0, "waypoints": []})

    def eta(distance, wind_kmh, temp_c, payload_kg):
        observed["eta_weather"] = (wind_kmh, temp_c)
        return {"baseline_eta_minutes": 4, "ml_predicted_eta_minutes": 5,
                "final_eta_minutes": 5, "model_name": "test", "reason": "test"}

    def safety(priority, weather_result, allocation, route, weight):
        observed["safety_weather"] = weather_result
        return {"status": "PASS", "score": 1, "reason": "test"}

    monkeypatch.setattr("app.api.endpoints.ai_decision.run_eta_agent", eta)
    monkeypatch.setattr("app.api.endpoints.ai_decision.run_safety_agent", safety)
    request = AIDecisionRequest(
        request_code="REQ-WEATHER-TEST", medical_type="BLOOD", urgency="HIGH", weight_kg=1,
        pickup=pickup, destination=destination, supplier_accepted=True,
    )
    result = await dispatch_graph.ainvoke({
        "workflow_stage": "DISPATCH_REVIEW", "request": request,
        "supplier_accepted": True, "supplier_candidates": [], "decision_trail": [],
    })

    assert result["final_status"] == "AWAITING_CENTRAL_HUB_REVIEW"
    assert result["weather_result"]["observations"] == weather["observations"]
    assert observed["eta_weather"] == (weather["wind_speed_kmh"], weather["temperature_c"])
    assert observed["safety_weather"] == weather
    assert result["decision_trail"][-3]["agent"] == "WeatherAgent"
    assert "Open-Meteo" in result["decision_trail"][-3]["reason"]


@pytest.mark.asyncio
async def test_weather_api_failure_blocks_flight_without_fabricating_values(monkeypatch):
    async def fetch(lat: float, lng: float):
        return False, {}, "API Timeout"

    monkeypatch.setattr("app.services.weather_client.fetch_current_weather", fetch)
    result = await run_weather_agent(GeoPoint(lat=12.97, lng=77.59), GeoPoint(lat=12.98, lng=77.61))

    assert result["flight_condition"] == "UNSAFE"
    assert result["wind_speed_kmh"] is None
    assert result["temperature_c"] is None
    assert result["observations"] == []
    assert result["model_prediction"] is None
    assert "API Timeout" in result["reason"]


@pytest.mark.asyncio
async def test_unrepresented_weather_category_fails_closed(monkeypatch):
    raw = weather_response(21.0, 5.0, "2026-10-06T14:00")
    raw["current"]["weather_code"] = 95  # Thunderstorm has no class in the trained dataset.

    async def fetch(lat: float, lng: float):
        return True, raw, ""

    monkeypatch.setattr("app.services.weather_client.fetch_current_weather", fetch)
    result = await run_weather_agent(GeoPoint(lat=12.97, lng=77.59), GeoPoint(lat=12.98, lng=77.61))

    assert result["flight_condition"] == "UNSAFE"
    assert result["model_prediction"] is None
    assert result["observations"] == []
    assert "not represented by the trained flight-condition model" in result["reason"]
