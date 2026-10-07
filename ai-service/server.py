"""
server.py
─────────
FastAPI Entrypoint for the Intelligent Medical Drone Multi-Agent AI Service.
Runs standalone on port 8000.
"""
import uvicorn
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.api.endpoints.ai_decision import ai_health, router as ai_decision_router

app = FastAPI(
    title="Intelligent Medical Drone AI Decision Microservice",
    version="2.0.0",
    description="Multi-Agent AI Decision Engine (Priority, Weather, Allocation, Route, Safety) for Bengaluru 4-Zone + Central Hub Architecture"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.get("/")
@app.get("/health")
@app.get("/api/health")
def health_check():
    return ai_health()

app.include_router(ai_decision_router)
app.include_router(ai_decision_router, prefix="/api")

if __name__ == "__main__":
    uvicorn.run("server:app", host="0.0.0.0", port=8000, reload=True)
