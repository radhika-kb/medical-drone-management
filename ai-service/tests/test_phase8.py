import pytest
from datetime import datetime, timezone
from unittest.mock import AsyncMock, patch

from app.schemas.medical_request import MedicalRequestResponse
from app.schemas.drone import DroneResponse
from app.agents.route_agent import compute_route
from app.agents.safety_agent import verify_safety
from app.core.config import get_settings

class MockDB:
    def __init__(self):
        self.agent_decisions = AsyncMock()
        self.agent_decisions.insert_one = AsyncMock(return_value=AsyncMock(inserted_id="mock_id"))
        
    def __getitem__(self, item):
        if item == "agent_decisions":
            return self.agent_decisions
        raise KeyError()

@pytest.fixture
def mock_db():
    return MockDB()

@pytest.fixture
def base_request():
    return MedicalRequestResponse(
        id="req-1",
        request_id="REQ-001",
        center_id="c-1",
        medical_type="BLOOD",
        quantity=1,
        weight_kg=2.0,
        urgency="HIGH",
        pickup={"lat": 12.9716, "lng": 77.5946},
        destination={"lat": 12.9800, "lng": 77.6100}, # ~ 1.9km away
        status="PENDING",
        created_at=datetime.now(timezone.utc)
    )

@pytest.fixture
def base_drone():
    return DroneResponse(
        id="d-1",
        drone_id="DRN-001",
        model="Zipline",
        battery_pct=100.0,
        max_payload_kg=5.0,
        max_range_km=40.0,
        latitude=12.9700,
        longitude=77.5900,
        status="AVAILABLE",
        zone="Zone A",
        updated_at=datetime.now(timezone.utc)
    )

def test_route_agent_calculations():
    # Distance drone->pickup (~0.53km) + pickup->dest (~1.9km) = ~2.44km
    result = compute_route(
        pickup_lat=12.9716, pickup_lng=77.5946,
        dest_lat=12.9800, dest_lng=77.6100,
        drone_lat=12.9700, drone_lng=77.5900
    )
    
    # Assert waypoints are correctly ordered
    assert len(result["route"]) == 3
    assert result["route"][0] == {"lat": 12.9700, "lng": 77.5900}
    assert result["route"][1] == {"lat": 12.9716, "lng": 77.5946}
    assert result["route"][2] == {"lat": 12.9800, "lng": 77.6100}
    
    # Assert distances and battery
    assert abs(result["distance_km"] - 2.44) < 0.1
    # 60 km/h = 1 km/min -> ~2.44 mins
    assert abs(result["eta_minutes"] - 2.44) < 0.1
    # 2% per km -> ~4.88%
    assert abs(result["battery_required_pct"] - 4.88) < 0.1

@pytest.mark.asyncio
async def test_safety_agent_pass(mock_db, base_request, base_drone):
    route_result = {
        "distance_km": 10.0,
        "eta_minutes": 10.0,
        "battery_required_pct": 20.0
    }
    weather = {"flight_condition": "SAFE"}
    
    result = await verify_safety(route_result, base_drone, base_request, weather, mock_db)
    assert result["decision"] == "PASS"

@pytest.mark.asyncio
async def test_safety_agent_weather_reject(mock_db, base_request, base_drone):
    route_result = {
        "distance_km": 10.0,
        "eta_minutes": 10.0,
        "battery_required_pct": 20.0
    }
    weather = {"flight_condition": "UNSAFE"}
    
    result = await verify_safety(route_result, base_drone, base_request, weather, mock_db)
    assert result["decision"] == "REJECT"

@pytest.mark.asyncio
async def test_safety_agent_weather_delay(mock_db, base_request, base_drone):
    route_result = {
        "distance_km": 10.0,
        "eta_minutes": 10.0,
        "battery_required_pct": 20.0
    }
    weather = {"flight_condition": "CAUTION"}
    req = base_request.model_copy(update={"urgency": "NORMAL"})
    
    result = await verify_safety(route_result, base_drone, req, weather, mock_db)
    assert result["decision"] == "DELAY"

@pytest.mark.asyncio
async def test_safety_agent_battery_reallocation(mock_db, base_request, base_drone):
    # Drone has 30% battery. Route requires 20%. Remaining = 10%. 
    # Minimum allowed is 15%.
    drone = base_drone.model_copy(update={"battery_pct": 30.0})
    route_result = {
        "distance_km": 10.0,
        "eta_minutes": 10.0,
        "battery_required_pct": 20.0
    }
    weather = {"flight_condition": "SAFE"}
    
    result = await verify_safety(route_result, drone, base_request, weather, mock_db)
    assert result["decision"] == "REQUEST_REALLOCATION"
    assert "Insufficient battery reserve" in result["reason"]
