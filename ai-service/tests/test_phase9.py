import pytest
import asyncio
from datetime import datetime, timezone
from unittest.mock import AsyncMock, patch, call

from app.schemas.mission import MissionResponse
from app.schemas.drone import DroneResponse
from app.schemas.mission_tracking import MissionTrackingCreate

@pytest.mark.asyncio
@pytest.mark.skip(reason="Legacy Mongo-backed app.simulation.engine has no active LangGraph/Spring equivalent; see tests/LEGACY_TESTS.md")
@patch("asyncio.sleep", new_callable=AsyncMock)
@patch("app.simulation.engine.get_mission_by_mission_id")
@patch("app.simulation.engine.get_drone_by_drone_id")
@patch("app.simulation.engine.save_tracking_update")
@patch("app.simulation.engine.update_mission_status")
@patch("app.simulation.engine.update_drone_status")
@patch("app.simulation.engine.manager")
async def test_run_mission_simulation(
    mock_manager, mock_update_drone_status, mock_update_mission_status,
    mock_save_tracking_update, mock_get_drone, mock_get_mission, mock_sleep
):
    mock_manager.broadcast_to_mission = AsyncMock()
    from app.simulation.engine import run_mission_simulation
    
    mock_db = AsyncMock()
    
    class MockCoordinate:
        def __init__(self, lat, lng):
            self.lat = lat
            self.lng = lng

    # Mission covers a very short distance
    mock_mission = MissionResponse(
        _id="m-1",
        mission_id="MIS-TEST",
        request_id="REQ-TEST",
        drone_id="DRN-TEST",
        route=[
            {"lat": 0.0, "lng": 0.0}, # Drone
            {"lat": 0.0, "lng": 0.01}, # Pickup (~1.1km)
            {"lat": 0.0, "lng": 0.02}  # Dest (~1.1km)
        ],
        distance_km=2.2,
        eta_minutes=2.2,
        battery_required_pct=4.4,
        status="ASSIGNED",
        started_at=datetime.now(timezone.utc)
    )
    mock_get_mission.return_value = mock_mission
    
    mock_drone = DroneResponse(
        id="d-1",
        drone_id="DRN-TEST",
        model="Test",
        battery_pct=100.0,
        max_payload_kg=5.0,
        max_range_km=40.0,
        latitude=0.0,
        longitude=0.0,
        status="ASSIGNED",
        zone="Zone A",
        updated_at=datetime.now(timezone.utc)
    )
    mock_get_drone.return_value = mock_drone
    
    await run_mission_simulation("MIS-TEST", mock_db)
    
    # Assert drone status restored to AVAILABLE at the end
    mock_update_drone_status.assert_called_with(mock_db, "d-1", "AVAILABLE")
    
    # Assert mission reached DELIVERED state
    mock_update_mission_status.assert_has_calls([
        call(mock_db, "MIS-TEST", "TRAVELLING_TO_PICKUP"),
        call(mock_db, "MIS-TEST", "PACKAGE_COLLECTED"),
        call(mock_db, "MIS-TEST", "IN_TRANSIT"),
        call(mock_db, "MIS-TEST", "DELIVERED")
    ], any_order=True)
    
    # Assert broadcasts were sent
    assert mock_manager.broadcast_to_mission.called
