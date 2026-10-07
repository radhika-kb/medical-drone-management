from typing import Any, Dict
from motor.motor_asyncio import AsyncIOMotorDatabase
from app.schemas.medical_request import MedicalRequestResponse
from app.schemas.drone import DroneResponse
from app.schemas.agent_decision import AgentDecisionCreate
from app.repositories.agent_decision_repository import save_agent_decision
from app.core.config import get_settings

async def verify_safety(
    route_result: Dict[str, Any],
    drone: DroneResponse,
    request: MedicalRequestResponse,
    weather: Dict[str, Any],
    db: AsyncIOMotorDatabase
) -> Dict[str, Any]:
    """
    Final deterministic gate before mission creation.
    Re-validates battery, weather, range, and payload.
    Outcomes: PASS, REJECT, DELAY, REQUEST_REALLOCATION.
    """
    settings = get_settings()
    decision = "PASS"
    reason = "All safety checks passed."
    
    # 1. Weather check
    if weather.get("flight_condition") == "UNSAFE":
        decision = "REJECT"
        reason = "Weather condition is UNSAFE."
    elif weather.get("flight_condition") == "CAUTION" and request.urgency not in ["HIGH", "CRITICAL"]:
        decision = "DELAY"
        reason = "Weather condition is CAUTION and request is not urgent enough."
        
    # 2. Payload check
    if decision == "PASS" and request.weight_kg > drone.max_payload_kg:
        decision = "REQUEST_REALLOCATION"
        reason = f"Request weight ({request.weight_kg}kg) exceeds drone payload capacity ({drone.max_payload_kg}kg)."
        
    # 3. Range check
    if decision == "PASS" and route_result["distance_km"] > drone.max_range_km:
        decision = "REQUEST_REALLOCATION"
        reason = f"Route distance ({route_result['distance_km']:.2f}km) exceeds drone max range ({drone.max_range_km}km)."
        
    # 4. Battery check
    if decision == "PASS":
        projected_battery = drone.battery_pct - route_result["battery_required_pct"]
        if projected_battery < settings.allocation_min_reserve_pct:
            decision = "REQUEST_REALLOCATION"
            reason = (f"Insufficient battery reserve. Current: {drone.battery_pct:.1f}%, "
                      f"Required: {route_result['battery_required_pct']:.1f}%, "
                      f"Projected: {projected_battery:.1f}%. Minimum allowed: {settings.allocation_min_reserve_pct}%")
                      
    # Persist decision
    decision_in = AgentDecisionCreate(
        agent_name="SafetyAgent",
        decision=decision,
        reason=reason,
        input_snapshot={
            "route_distance_km": route_result["distance_km"],
            "battery_required_pct": route_result["battery_required_pct"],
            "drone_battery_pct": drone.battery_pct,
            "drone_status": drone.status,
            "weather_condition": weather.get("flight_condition")
        }
    )
    
    await save_agent_decision(db, decision_in)
    
    return {
        "decision": decision,
        "reason": reason
    }
