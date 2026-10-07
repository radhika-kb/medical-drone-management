from datetime import datetime, timezone
from typing import Any, Dict, Optional
from pydantic import BaseModel, Field

class AgentDecisionCreate(BaseModel):
    """Fields to record an agent's decision trail."""
    agent_name: str = Field(..., description="Name of the agent, e.g., 'PriorityAgent', 'AllocationAgent'")
    decision: str = Field(..., description="The final decision or outcome")
    score: Optional[float] = Field(None, description="The computed score, if applicable")
    reason: str = Field(..., description="Human-readable explanation of the decision")
    input_snapshot: Dict[str, Any] = Field(..., description="Snapshot of the input data provided to the agent")
    timestamp: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))

class AgentDecision(AgentDecisionCreate):
    """Full database model for an agent decision."""
    id: str
