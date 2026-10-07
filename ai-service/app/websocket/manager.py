from typing import Dict, List
from fastapi import WebSocket
import json

class ConnectionManager:
    def __init__(self):
        # Maps mission_id -> list of active WebSocket connections
        self.active_connections: Dict[str, List[WebSocket]] = {}

    async def connect(self, websocket: WebSocket, mission_id: str):
        await websocket.accept()
        if mission_id not in self.active_connections:
            self.active_connections[mission_id] = []
        self.active_connections[mission_id].append(websocket)

    def disconnect(self, websocket: WebSocket, mission_id: str):
        if mission_id in self.active_connections:
            if websocket in self.active_connections[mission_id]:
                self.active_connections[mission_id].remove(websocket)
            if not self.active_connections[mission_id]:
                del self.active_connections[mission_id]

    async def broadcast_to_mission(self, mission_id: str, data: dict):
        if mission_id in self.active_connections:
            # We must handle formatting datetime inside data dict before sending
            for connection in self.active_connections[mission_id]:
                try:
                    await connection.send_text(json.dumps(data, default=str))
                except Exception:
                    pass

manager = ConnectionManager()
