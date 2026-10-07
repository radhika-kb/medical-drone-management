from typing import Any, Dict, List, Optional
from motor.motor_asyncio import AsyncIOMotorDatabase
from app.schemas.medical_request import MedicalRequestResponse
from app.schemas.drone import DroneResponse
from app.schemas.agent_decision import AgentDecisionCreate
from app.repositories.agent_decision_repository import save_agent_decision
from app.core.config import get_settings
from app.utils.geo import calculate_distance_km

PRIORITY_COMPATIBILITY_SCORES = {
    "CRITICAL": 1.0,
    "HIGH": 0.8,
    "MEDIUM": 0.5,
    "NORMAL": 0.2
}

async def allocate_drone(
    request: MedicalRequestResponse, 
    priority: str, 
    drones: List[DroneResponse], 
    db: AsyncIOMotorDatabase
) -> Dict[str, Any]:
    """
    Filter and score drones for a mission based on priority and operational limits.
    """
    settings = get_settings()
    
    # 1. Candidate Filtering
    feasible_drones = []
    filtering_reasons = []
    
    # Calculate mission distance: pickup to destination
    mission_distance_km = calculate_distance_km(
        request.pickup.lat, request.pickup.lng,
        request.destination.lat, request.destination.lng
    )
    
    for drone in drones:
        if drone.status != "AVAILABLE":
            filtering_reasons.append(f"{drone.drone_id} excluded: Status {drone.status}")
            continue
            
        if drone.max_payload_kg < request.weight_kg:
            filtering_reasons.append(f"{drone.drone_id} excluded: Payload limit ({drone.max_payload_kg}kg < {request.weight_kg}kg)")
            continue
            
        # Total distance including travel to pickup
        distance_to_pickup = calculate_distance_km(
            drone.latitude, drone.longitude,
            request.pickup.lat, request.pickup.lng
        )
        total_distance = distance_to_pickup + mission_distance_km
        
        required_range = total_distance * settings.allocation_safety_factor
        if drone.max_range_km < required_range:
            filtering_reasons.append(f"{drone.drone_id} excluded: Range limit ({drone.max_range_km}km < {required_range:.2f}km required)")
            continue
            
        # Battery check: estimate battery drain. Simplified model: (required_range / max_range) * 100
        battery_drain = (required_range / drone.max_range_km) * 100
        projected_battery = drone.battery_pct - battery_drain
        
        if projected_battery < settings.allocation_min_reserve_pct:
            filtering_reasons.append(f"{drone.drone_id} excluded: Battery reserve ({projected_battery:.1f}% < {settings.allocation_min_reserve_pct}%)")
            continue
            
        feasible_drones.append({
            "drone": drone,
            "total_distance": total_distance,
            "projected_battery": projected_battery
        })
        
    # 2. Scoring
    if not feasible_drones:
        decision_in = AgentDecisionCreate(
            agent_name="AllocationAgent",
            decision="NO_FEASIBLE_DRONE",
            reason=f"No feasible drones. Filtering log: " + "; ".join(filtering_reasons),
            input_snapshot={"request_id": request.id, "priority": priority, "drone_count": len(drones)}
        )
        await save_agent_decision(db, decision_in)
        return {
            "decision": "NO_FEASIBLE_DRONE",
            "drone_id": None,
            "score": None,
            "reason": decision_in.reason
        }
        
    best_drone_candidate = None
    best_score = -1.0
    scored_candidates = []
    
    for candidate in feasible_drones:
        drone = candidate["drone"]
        
        # Calculate components
        battery_score = min(candidate["projected_battery"] / 100.0, 1.0)
        
        # Distance score: closer is better. If distance is 0, score is 1.0. Otherwise inverse.
        # Max reasonable distance within city is ~50km.
        distance_score = max(0.0, 1.0 - (candidate["total_distance"] / 50.0))
        
        payload_score = min(request.weight_kg / drone.max_payload_kg, 1.0)
        
        availability_score = 1.0 if drone.status == "AVAILABLE" else 0.0
        
        priority_compatibility = PRIORITY_COMPATIBILITY_SCORES.get(priority, 0.5)
        
        # Apply weights exactly per spec
        total_score = (
            0.30 * battery_score + 
            0.25 * distance_score + 
            0.20 * payload_score + 
            0.15 * availability_score + 
            0.10 * priority_compatibility
        )
        
        scored_candidates.append({
            "drone_id": drone.drone_id,
            "score": total_score,
            "components": {
                "battery": battery_score,
                "distance": distance_score,
                "payload": payload_score,
                "availability": availability_score,
                "priority": priority_compatibility
            }
        })
        
        if total_score > best_score:
            best_score = total_score
            best_drone_candidate = drone
            
    # 3. Persist Decision
    reason = f"Selected {best_drone_candidate.drone_id} with score {best_score:.4f}. Scored {len(feasible_drones)} candidates."
    decision_in = AgentDecisionCreate(
        agent_name="AllocationAgent",
        decision=best_drone_candidate.drone_id,
        score=best_score,
        reason=reason,
        input_snapshot={
            "request_id": request.id,
            "priority": priority,
            "scored_candidates": scored_candidates
        }
    )
    
    await save_agent_decision(db, decision_in)
    
    return {
        "decision": best_drone_candidate.drone_id,
        "drone_id": best_drone_candidate.id, # The internal MongoDB ID
        "score": best_score,
        "reason": reason,
        "scored_candidates": scored_candidates
    }
