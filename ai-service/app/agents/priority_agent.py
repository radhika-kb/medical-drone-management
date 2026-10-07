from datetime import datetime, timezone
from typing import Any, Dict
from motor.motor_asyncio import AsyncIOMotorDatabase
from app.schemas.medical_request import MedicalRequestResponse
from app.schemas.agent_decision import AgentDecisionCreate
from app.repositories.agent_decision_repository import save_agent_decision
from app.core.config import get_settings

# Tiers ordered by integer value for easy escalation
PRIORITY_TIERS = {
    "NORMAL": 1,
    "MEDIUM": 2,
    "HIGH": 3,
    "CRITICAL": 4
}
REVERSE_PRIORITY_TIERS = {v: k for k, v in PRIORITY_TIERS.items()}

async def assess_priority(request: MedicalRequestResponse, db: AsyncIOMotorDatabase) -> Dict[str, Any]:
    """
    Determine Priority of a medical request based on type, urgency, and wait time.
    """
    settings = get_settings()
    now = datetime.now(timezone.utc)
    
    # Calculate age in minutes
    if request.created_at.tzinfo is None:
        created_at_aware = request.created_at.replace(tzinfo=timezone.utc)
    else:
        created_at_aware = request.created_at
        
    wait_time_minutes = (now - created_at_aware).total_seconds() / 60.0
    
    # 1. Base Priority Mapping
    base_priority = "NORMAL"
    
    # Critical overrides based on medical type and urgency
    if request.medical_type in ["BLOOD", "ORGANS"] and request.urgency == "CRITICAL":
        base_priority = "CRITICAL"
    elif request.medical_type in ["BLOOD", "ORGANS", "EMERGENCY_MEDICINE"]:
        base_priority = "HIGH"
    elif request.urgency == "HIGH":
        base_priority = "HIGH"
    elif request.urgency == "MEDIUM" or request.medical_type in ["VACCINES", "PLASMA", "INSULIN"]:
        base_priority = "MEDIUM"
        
    current_tier_val = PRIORITY_TIERS[base_priority]
    
    # 2. Escalation Logic
    escalated = False
    if wait_time_minutes > settings.priority_wait_threshold_minutes:
        if current_tier_val < 4:
            current_tier_val += 1
            escalated = True
            
    final_priority = REVERSE_PRIORITY_TIERS[current_tier_val]
    
    reason = f"Base priority {base_priority} based on type={request.medical_type} and urgency={request.urgency}."
    if escalated:
        reason += f" Escalated to {final_priority} because wait time ({wait_time_minutes:.1f}m) exceeded threshold ({settings.priority_wait_threshold_minutes}m)."
    
    # 3. Persist Decision
    decision_in = AgentDecisionCreate(
        agent_name="PriorityAgent",
        decision=final_priority,
        score=float(current_tier_val),
        reason=reason,
        input_snapshot={
            "request_id": request.id,
            "medical_type": request.medical_type,
            "urgency": request.urgency,
            "wait_time_minutes": wait_time_minutes,
            "priority_wait_threshold_minutes": settings.priority_wait_threshold_minutes
        }
    )
    
    await save_agent_decision(db, decision_in)
    
    return {
        "priority": final_priority,
        "reason": reason
    }
