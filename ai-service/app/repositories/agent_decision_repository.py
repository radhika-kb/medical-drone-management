from motor.motor_asyncio import AsyncIOMotorDatabase
from app.schemas.agent_decision import AgentDecisionCreate, AgentDecision

async def save_agent_decision(db: AsyncIOMotorDatabase, decision_in: AgentDecisionCreate) -> AgentDecision:
    """Save a new agent decision into the agent_decisions collection."""
    decision_dict = decision_in.model_dump()
    result = await db["agent_decisions"].insert_one(decision_dict)
    
    return AgentDecision(
        id=str(result.inserted_id),
        **decision_dict
    )
