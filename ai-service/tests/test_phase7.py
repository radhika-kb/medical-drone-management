import pytest
from datetime import datetime, timedelta, timezone
from unittest.mock import AsyncMock

from app.schemas.medical_request import MedicalRequestResponse
from app.schemas.drone import DroneResponse
from app.agents.priority_agent import assess_priority
from app.agents.allocation_agent import allocate_drone
from app.core.config import get_settings

# Mock DB
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
def base_drones():
    return [
        DroneResponse(
            id="d-1",
            drone_id="DRN-001",
            model="Zipline",
            battery_pct=100.0,
            max_payload_kg=5.0,
            max_range_km=40.0,
            latitude=12.9700,
            longitude=77.5900, # close to pickup
            status="AVAILABLE",
            zone="Zone A",
            updated_at=datetime.now(timezone.utc)
        ),
        DroneResponse(
            id="d-2",
            drone_id="DRN-002",
            model="Zipline",
            battery_pct=50.0,
            max_payload_kg=1.0, # Too low payload for 2.0kg request
            max_range_km=40.0,
            latitude=12.9700,
            longitude=77.5900,
            status="AVAILABLE",
            zone="Zone A",
            updated_at=datetime.now(timezone.utc)
        )
    ]

@pytest.mark.asyncio
async def test_priority_agent_critical_blood(mock_db, base_request):
    """A Critical/Blood request scores as Critical immediately."""
    req = base_request.model_copy(update={"urgency": "CRITICAL", "medical_type": "BLOOD"})
    
    result = await assess_priority(req, mock_db)
    
    assert result["priority"] == "CRITICAL"
    assert "Base priority CRITICAL" in result["reason"]

@pytest.mark.asyncio
async def test_priority_agent_escalation(mock_db, base_request):
    """A HIGH request waiting past the threshold escalates to CRITICAL."""
    # created 40 mins ago (threshold is 30)
    old_time = datetime.now(timezone.utc) - timedelta(minutes=40)
    req = base_request.model_copy(update={"urgency": "HIGH", "medical_type": "BLOOD", "created_at": old_time})
    
    result = await assess_priority(req, mock_db)
    
    assert result["priority"] == "CRITICAL"
    assert "Escalated to CRITICAL" in result["reason"]

@pytest.mark.asyncio
async def test_allocation_filtering_payload(mock_db, base_request, base_drones):
    """A drone with insufficient payload is correctly filtered out before scoring."""
    result = await allocate_drone(base_request, "CRITICAL", base_drones, mock_db)
    
    assert result["decision"] == "DRN-001"
    
    # DRN-002 should not be in the scored candidates
    scored_drones = [c["drone_id"] for c in mock_db.agent_decisions.insert_one.call_args[0][0]["input_snapshot"]["scored_candidates"]]
    assert "DRN-002" not in scored_drones
    assert "DRN-001" in scored_drones
    
@pytest.mark.asyncio
async def test_allocation_filtering_battery_boundary(mock_db, base_request, base_drones):
    """Test battery reserve boundary logic."""
    # Distance drone->pickup is ~0.53km, pickup->dest is ~1.9km. Total ~ 2.43km.
    # safety_factor=1.2 -> required=2.916km.
    # max_range=10km -> drain=29.16%.
    # min_reserve=15%. So battery needs to be > 44.16%.
    
    drone = base_drones[0].model_copy(update={"max_range_km": 10.0})
    
    # Borderline PASS
    drone.battery_pct = 44.3
    res1 = await allocate_drone(base_request, "HIGH", [drone], mock_db)
    assert res1["decision"] == "DRN-001"
    
    # Borderline FAIL
    drone.battery_pct = 44.2
    res2 = await allocate_drone(base_request, "HIGH", [drone], mock_db)
    assert res2["decision"] == "NO_FEASIBLE_DRONE"

@pytest.mark.asyncio
async def test_allocation_scoring_comparison(mock_db, base_request, base_drones):
    """Two feasible drones with different battery/distance produce different scores, and the higher wins."""
    # Both drones can carry the payload
    d1 = base_drones[0].model_copy(update={"battery_pct": 100.0, "latitude": 12.9700}) # closer
    d2 = base_drones[0].model_copy(update={"drone_id": "DRN-002", "battery_pct": 50.0, "latitude": 12.9000}) # further, less battery
    
    result = await allocate_drone(base_request, "MEDIUM", [d1, d2], mock_db)
    
    assert result["decision"] == "DRN-001"
    
    # Verify scores are present and different
    scores = {c["drone_id"]: c["score"] for c in result["scored_candidates"]}
    assert scores["DRN-001"] > scores["DRN-002"]
    
@pytest.mark.asyncio
async def test_allocation_no_feasible_drones(mock_db, base_request):
    """Zero feasible drones returns a clean 'no feasible drone' response."""
    result = await allocate_drone(base_request, "MEDIUM", [], mock_db)
    assert result["decision"] == "NO_FEASIBLE_DRONE"
    assert result["drone_id"] is None
