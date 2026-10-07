from fastapi import APIRouter, WebSocket, WebSocketDisconnect, Depends
from motor.motor_asyncio import AsyncIOMotorDatabase
from app.db.mongodb import get_database
from app.websocket.manager import manager
from app.core.logging import get_logger

logger = get_logger(__name__)

router = APIRouter(prefix="/ws", tags=["websocket"])

@router.websocket("/missions/{mission_id}")
async def websocket_endpoint(
    websocket: WebSocket,
    mission_id: str,
):
    await manager.connect(websocket, mission_id)
    logger.info(f"Client connected to mission tracking {mission_id}")
    
    try:
        while True:
            # We just keep the connection open. Client sends nothing for now.
            data = await websocket.receive_text()
    except WebSocketDisconnect:
        manager.disconnect(websocket, mission_id)
        logger.info(f"Client disconnected from mission tracking {mission_id}")
