"""Regression tests for the stateless LangGraph workflow used by Spring Boot."""
from __future__ import annotations

import pytest

from app.api.endpoints.ai_decision import (
    AIDecisionRequest, DroneCandidate, GeoPoint, NoFlyZoneModel,
    run_route_agent, run_weather_agent as active_weather_agent,
)
from app.api.endpoints import ai_decision
from app.orchestration.graph import dispatch_graph


PICKUP = GeoPoint(lat=12.90, lng=77.50)
DESTINATION = GeoPoint(lat=12.91, lng=77.52)


def test_no_fly_zone_conflict_rejects_route_when_endpoint_is_inside_zone():
    zone = NoFlyZoneModel(name="Test restricted airspace", center_lat=PICKUP.lat,
                          center_lng=PICKUP.lng, radius_km=1.0)

    route = run_route_agent(PICKUP.lat, PICKUP.lng, PICKUP, DESTINATION, [zone])

    assert route["status"] == "UNSAFE_NO_ROUTE"
    assert route["waypoints"] == []
    assert "inside a no-fly zone" in route["reason"]


def make_request(*, supplier_accepted=True, drones=None):
    return AIDecisionRequest(
        request_id=17,
        request_code="REQ-CURRENT-17",
        medical_type="BLOOD",
        urgency="HIGH",
        weight_kg=1.5,
        pickup=PICKUP,
        destination=DESTINATION,
        preferred_zone="SOUTH_ZONE",
        required_quantity=3,
        supplier_accepted=supplier_accepted,
        supplier_candidates=[{
            "hospital_id": 8,
            "hospital_name": "Supplier Hospital",
            "latitude": PICKUP.lat,
            "longitude": PICKUP.lng,
            "available_quantity": 5,
            "reserved_quantity": 1,
            "verification_status": "APPROVED",
        }],
        drones=drones if drones is not None else [DroneCandidate(
            id=4, drone_code="DR-4", model="Medical-X", battery_pct=95,
            max_payload_kg=5, max_range_km=40,
            latitude=PICKUP.lat, longitude=PICKUP.lng,
        )],
    )


def install_successful_agents(monkeypatch, *, weather_status="SAFE", safety_status="PASS", calls=None):
    calls = calls if calls is not None else []

    def priority(medical_type, urgency, weight_kg):
        calls.append(("priority", medical_type, urgency, weight_kg))
        return {"priority_level": "HIGH", "priority_score": 82, "reason": "urgent blood request"}

    def supply(destination, quantity, candidates):
        calls.append(("supply", destination, quantity, candidates))
        return {"status": "MATCH_FOUND", "reason": "verified stock available",
                "recommended_supplier": {"hospital_id": 8, "match_score": 0.95}}

    def allocation(drones, pickup, destination, weight_kg, preferred_zone):
        calls.append(("allocation", drones, pickup, destination, weight_kg, preferred_zone))
        if not drones:
            return {"decision": "NO_FEASIBLE_DRONE", "reason": "no available aircraft"}
        drone = drones[0]
        return {"decision": "ALLOCATED", "drone_code": drone.drone_code,
                "drone_lat": drone.latitude, "drone_lng": drone.longitude,
                "score": 0.91, "reason": "payload and range are sufficient"}

    def route(drone_lat, drone_lng, pickup, destination, no_fly_zones):
        calls.append(("route", drone_lat, drone_lng, pickup, destination, no_fly_zones))
        return {"status": "SAFE", "algorithm": "test-route", "reason": "clear route",
                "distance_km": 3.2, "eta_minutes": 4, "waypoints": [
                    {"lat": pickup.lat, "lng": pickup.lng},
                    {"lat": destination.lat, "lng": destination.lng},
                ]}

    async def weather(pickup, destination, custom_weather=None):
        calls.append(("weather", pickup, destination))
        return {"flight_condition": weather_status,
                "temperature_c": 28.5, "wind_speed_kmh": 13.2,
                "confidence": 0.94, "model_prediction": weather_status,
                "model_name": "weather-test-model", "flight_suitability": weather_status == "SAFE",
                "reason": "Open-Meteo observations evaluated by test model",
                "observations": [{"location": "pickup"}, {"location": "destination"}]}

    def eta(distance_km, wind_kmh, temp_c, payload_kg):
        calls.append(("eta", distance_km, wind_kmh, temp_c, payload_kg))
        return {"baseline_eta_minutes": 4, "ml_predicted_eta_minutes": 5,
                "final_eta_minutes": 5, "model_name": "eta-test-model", "reason": "test ETA"}

    def safety(priority, weather_result, allocation_result, route_result, weight_kg):
        calls.append(("safety", priority, weather_result, allocation_result, route_result, weight_kg))
        return {"status": safety_status, "score": 0.9,
                "reason": "safe for review" if safety_status == "PASS" else "safety policy rejected flight"}

    monkeypatch.setattr(ai_decision, "run_priority_agent", priority)
    monkeypatch.setattr(ai_decision, "run_supply_match_agent", supply)
    monkeypatch.setattr(ai_decision, "run_allocation_agent", allocation)
    monkeypatch.setattr(ai_decision, "run_route_agent", route)
    monkeypatch.setattr(ai_decision, "run_weather_agent", weather)
    monkeypatch.setattr(ai_decision, "run_eta_agent", eta)
    monkeypatch.setattr(ai_decision, "run_safety_agent", safety)
    return calls


@pytest.mark.asyncio
async def test_active_workflow_reaches_central_hub_review_and_passes_current_fields(monkeypatch):
    calls = install_successful_agents(monkeypatch)
    result = await ai_decision.run_langgraph_workflow(make_request())

    assert result["status"] == "APPROVED"
    assert result["workflow_status"] == "AWAITING_CENTRAL_HUB_REVIEW"
    assert [row["agent"] for row in result["decision_trail"]] == [
        "PriorityAgent", "SupplyMatchingAgent", "SupplierWorkflow", "AllocationAgent",
        "RouteAgent", "WeatherAgent", "ETAPredictionAgent", "SafetyAgent",
    ]
    assert calls[0] == ("priority", "BLOOD", "HIGH", 1.5)
    supply_call = next(call for call in calls if call[0] == "supply")
    assert supply_call[1] == DESTINATION
    assert supply_call[2] == 3  # required_quantity is passed, not inferred from a legacy schema.
    assert len(supply_call[3]) == 1
    eta_call = next(call for call in calls if call[0] == "eta")
    assert eta_call[2:] == (13.2, 28.5, 1.5)
    assert result["route"]["baseline_eta_minutes"] == 4
    assert result["route"]["ml_predicted_eta_minutes"] == 5


@pytest.mark.asyncio
async def test_supplier_wait_state_stops_before_allocation(monkeypatch):
    calls = install_successful_agents(monkeypatch)
    result = await ai_decision.run_langgraph_workflow(make_request(supplier_accepted=False))

    assert result["workflow_status"] == "WAIT_FOR_SUPPLIER"
    assert [row["agent"] for row in result["decision_trail"]] == [
        "PriorityAgent", "SupplyMatchingAgent", "SupplierWorkflow",
    ]
    assert not any(call[0] in {"allocation", "route", "weather", "eta", "safety"} for call in calls)


@pytest.mark.asyncio
async def test_no_feasible_drone_stops_before_route_and_weather(monkeypatch):
    calls = install_successful_agents(monkeypatch)
    result = await ai_decision.run_langgraph_workflow(make_request(drones=[]))

    assert result["workflow_status"] == "NO_FEASIBLE_DRONE"
    assert not any(call[0] in {"route", "weather", "eta", "safety"} for call in calls)


@pytest.mark.asyncio
async def test_unsafe_weather_rejects_and_skips_eta_and_safety(monkeypatch):
    calls = install_successful_agents(monkeypatch, weather_status="UNSAFE")
    result = await ai_decision.run_langgraph_workflow(make_request())

    assert result["status"] == "REJECTED_WEATHER"
    assert result["weather"]["flight_condition"] == "UNSAFE"
    assert not any(call[0] in {"eta", "safety"} for call in calls)


@pytest.mark.asyncio
async def test_live_weather_api_failure_blocks_hub_review(monkeypatch):
    install_successful_agents(monkeypatch)

    async def unavailable(lat, lng):
        return False, {}, "API Timeout"

    # Run the actual active weather agent in the graph, with only its HTTP boundary mocked.
    from app.services import weather_client
    monkeypatch.setattr(ai_decision, "run_weather_agent", active_weather_agent)
    monkeypatch.setattr(weather_client, "fetch_current_weather", unavailable)

    result = await ai_decision.run_langgraph_workflow(make_request())

    assert result["status"] == "REJECTED_WEATHER"
    assert result["weather"]["observations"] == []
    assert "API Timeout" in result["weather"]["reason"]
    assert result["eta"] is None
    assert result["safety"] is None


@pytest.mark.asyncio
async def test_weather_model_failure_blocks_hub_review(monkeypatch):
    install_successful_agents(monkeypatch)

    async def live_weather(lat, lng):
        return True, {
            "source": "Open-Meteo",
            "current": {"time": "2026-10-06T14:00", "temp_c": 29.4,
                        "humidity": 61, "precip_mm": 0.2, "wind_kph": 14.2,
                        "gust_kph": 20.1, "wind_degree": 135, "vis_km": 8.4,
                        "pressure_mb": 1006.7, "cloud": 35, "dewpoint_c": 19.0,
                        "uv": 4.0, "weather_code": 61,
                        "condition": {"text": "Rain"}},
        }, ""

    from app.services import weather_client
    monkeypatch.setattr(ai_decision, "run_weather_agent", active_weather_agent)
    monkeypatch.setattr(weather_client, "fetch_current_weather", live_weather)
    monkeypatch.setattr(ai_decision, "get_model", lambda name: None)

    result = await ai_decision.run_langgraph_workflow(make_request())

    assert result["status"] == "REJECTED_WEATHER"
    assert "model is unavailable" in result["weather"]["reason"]
    assert result["eta"] is None
    assert result["safety"] is None


@pytest.mark.asyncio
async def test_safety_rejection_never_becomes_central_hub_approval(monkeypatch):
    install_successful_agents(monkeypatch, safety_status="REJECTED_SAFETY")
    result = await ai_decision.run_langgraph_workflow(make_request())

    assert result["status"] == "REJECTED_SAFETY"
    assert result["workflow_status"] == "REJECTED_SAFETY"
    assert result["safety"]["status"] == "REJECTED_SAFETY"


@pytest.mark.asyncio
async def test_decision_trail_is_stable_across_repeated_recommendations(monkeypatch):
    install_successful_agents(monkeypatch)
    request = make_request()
    first = await ai_decision.run_langgraph_workflow(request)
    second = await ai_decision.run_langgraph_workflow(request)

    first_trail = [(row["agent"], row["decision"], row["reason"]) for row in first["decision_trail"]]
    second_trail = [(row["agent"], row["decision"], row["reason"]) for row in second["decision_trail"]]
    assert first_trail == second_trail


@pytest.mark.asyncio
async def test_active_graph_does_not_create_mission_or_call_an_llm(monkeypatch):
    install_successful_agents(monkeypatch)
    result = await ai_decision.run_langgraph_workflow(make_request())

    assert result["workflow_status"] == "AWAITING_CENTRAL_HUB_REVIEW"
    assert "mission" not in result
    assert "llm_explanation" not in result

