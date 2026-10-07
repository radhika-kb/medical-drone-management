import uuid
from typing import List, Optional
from bson import ObjectId
from motor.motor_asyncio import AsyncIOMotorDatabase
from app.schemas.mission import MissionCreate, MissionResponse

_COLLECTION = "missions"

def _to_oid(id_str: str) -> Optional[ObjectId]:
    try:
        return ObjectId(id_str)
    except Exception:
        return None

async def create_mission(db: AsyncIOMotorDatabase, mission_in: MissionCreate) -> MissionResponse:
    mission_dict = mission_in.model_dump()
    mission_dict["mission_id"] = f"MIS-{str(uuid.uuid4())[:8].upper()}"
    mission_dict["status"] = "ASSIGNED"
    
    import datetime
    mission_dict["started_at"] = datetime.datetime.now(datetime.timezone.utc)
    
    result = await db[_COLLECTION].insert_one(mission_dict)
    mission_dict["id"] = str(result.inserted_id)
    if "_id" in mission_dict:
        del mission_dict["_id"]
    
    return MissionResponse(**mission_dict)

async def get_mission(db: AsyncIOMotorDatabase, mission_id: str) -> Optional[MissionResponse]:
    oid = _to_oid(mission_id)
    if oid is None:
        return None
        
    doc = await db[_COLLECTION].find_one({"_id": oid})
    if doc:
        doc["id"] = str(doc.pop("_id"))
        return MissionResponse(**doc)
    return None


async def list_missions(
    db: AsyncIOMotorDatabase,
    skip: int = 0,
    limit: int = 50,
) -> List[MissionResponse]:
    cursor = db[_COLLECTION].find({}).sort("started_at", -1).skip(skip).limit(limit)
    missions = []
    async for doc in cursor:
        doc["id"] = str(doc.pop("_id"))
        missions.append(MissionResponse(**doc))
    return missions


async def get_mission_by_mission_id(
    db: AsyncIOMotorDatabase,
    mission_id: str,          # the "MIS-XXXX" string, not ObjectId
) -> Optional[MissionResponse]:
    doc = await db[_COLLECTION].find_one({"mission_id": mission_id})
    if doc:
        doc["id"] = str(doc.pop("_id"))
        return MissionResponse(**doc)
    return None
