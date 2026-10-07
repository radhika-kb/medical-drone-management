from motor.motor_asyncio import AsyncIOMotorDatabase
from app.schemas.mission_tracking import MissionTrackingCreate, MissionTrackingResponse

_COLLECTION = "mission_tracking"

async def save_tracking_update(db: AsyncIOMotorDatabase, tracking_in: MissionTrackingCreate) -> MissionTrackingResponse:
    tracking_dict = tracking_in.model_dump()
    result = await db[_COLLECTION].insert_one(tracking_dict)
    tracking_dict["_id"] = str(result.inserted_id)
    return MissionTrackingResponse(**tracking_dict)

async def update_mission_status(db: AsyncIOMotorDatabase, mission_id: str, new_status: str) -> None:
    await db["missions"].update_one(
        {"mission_id": mission_id},
        {"$set": {"status": new_status}}
    )
