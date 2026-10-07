"""Unit regressions for the active Open-Meteo client and weather inference."""
from __future__ import annotations

import httpx
import pytest

from app.api.endpoints.ai_decision import GeoPoint, run_weather_agent
from app.services import weather_client


def open_meteo_payload():
    return {"timezone": "Asia/Kolkata", "current": {
        "time": "2026-10-06T14:00",
        "temperature_2m": 29.4,
        "relative_humidity_2m": 61,
        "precipitation": 0.2,
        "weather_code": 61,
        "pressure_msl": 1008.1,
        "surface_pressure": 1006.7,
        "cloud_cover": 35,
        "dew_point_2m": 19.0,
        "wind_speed_10m": 14.2,
        "wind_direction_10m": 135,
        "wind_gusts_10m": 20.1,
        "visibility": 8400,
        "uv_index": 4.0,
    }}


class FakeResponse:
    def __init__(self, payload):
        self.payload = payload

    def raise_for_status(self):
        return None

    def json(self):
        return self.payload


@pytest.mark.asyncio
async def test_client_calls_configured_open_meteo_and_maps_real_features(monkeypatch):
    calls = {}

    class FakeAsyncClient:
        def __init__(self, timeout):
            calls["timeout"] = timeout

        async def __aenter__(self):
            return self

        async def __aexit__(self, *args):
            return None

        async def get(self, url, params):
            calls["url"] = url
            calls["params"] = params
            return FakeResponse(open_meteo_payload())

    monkeypatch.setattr(weather_client, "WEATHER_API_BASE_URL", "https://weather.example.test/v1/forecast")
    monkeypatch.setattr(weather_client, "WEATHER_API_TIMEOUT_SECONDS", 3.5)
    monkeypatch.setattr(weather_client.httpx, "AsyncClient", FakeAsyncClient)

    ok, result, error = await weather_client.fetch_current_weather(12.9, 77.5)

    assert ok is True and error == ""
    assert calls["url"] == "https://weather.example.test/v1/forecast"
    assert calls["timeout"] == 3.5
    assert calls["params"]["latitude"] == 12.9
    assert calls["params"]["longitude"] == 77.5
    assert "temperature_2m" in calls["params"]["current"]
    assert result["source"] == "Open-Meteo"
    assert result["current"] == {
        "time": "2026-10-06T14:00", "temp_c": 29.4, "humidity": 61,
        "precip_mm": 0.2, "wind_kph": 14.2, "gust_kph": 20.1,
        "wind_degree": 135, "vis_km": 8.4, "pressure_mb": 1006.7,
        "cloud": 35, "dewpoint_c": 19.0, "uv": 4.0,
        "condition": {"text": "Rain"}, "weather_code": 61,
    }


@pytest.mark.asyncio
async def test_client_reports_timeout_without_fabricating_observations(monkeypatch):
    class TimeoutClient:
        def __init__(self, timeout):
            pass

        async def __aenter__(self):
            return self

        async def __aexit__(self, *args):
            return None

        async def get(self, *args, **kwargs):
            raise httpx.TimeoutException("timed out")

    monkeypatch.setattr(weather_client.httpx, "AsyncClient", TimeoutClient)
    ok, raw, error = await weather_client.fetch_current_weather(12.9, 77.5)
    assert ok is False
    assert raw == {}
    assert error == "API Timeout"


@pytest.mark.asyncio
async def test_live_weather_model_failure_fails_closed(monkeypatch):
    async def fetch(lat, lng):
        return True, open_meteo_payload_for_agent(), ""

    monkeypatch.setattr(weather_client, "fetch_current_weather", fetch)
    monkeypatch.setattr("app.api.endpoints.ai_decision.get_model", lambda name: None)

    result = await run_weather_agent(
        GeoPoint(lat=12.9, lng=77.5), GeoPoint(lat=12.91, lng=77.52)
    )

    assert result["flight_condition"] == "UNSAFE"
    assert result["flight_suitability"] is False
    assert result["model_prediction"] is None
    assert result["observations"] == []
    assert "model is unavailable" in result["reason"]


def open_meteo_payload_for_agent():
    payload = open_meteo_payload()
    current = payload["current"]
    current.update({
        "temp_c": current["temperature_2m"], "humidity": current["relative_humidity_2m"],
        "precip_mm": current["precipitation"], "wind_kph": current["wind_speed_10m"],
        "gust_kph": current["wind_gusts_10m"], "wind_degree": current["wind_direction_10m"],
        "vis_km": current["visibility"] / 1000.0, "pressure_mb": current["surface_pressure"],
        "cloud": current["cloud_cover"], "dewpoint_c": current["dew_point_2m"],
        "uv": current["uv_index"], "condition": {"text": "Rain"},
    })
    payload["source"] = "Open-Meteo"
    return payload
