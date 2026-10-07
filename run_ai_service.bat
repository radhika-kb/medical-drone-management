@echo off
title Medical Drone - FastAPI AI Service (:8000)
echo ========================================================
echo Starting FastAPI AI Decision Microservice (Port 8000)
echo Multi-Agent AI (Priority, Weather, Allocation, Route, Safety)
echo ========================================================
cd /d "%~dp0ai-service"

if not exist "venv" (
    echo Virtual environment not found. Creating venv in ai-service...
    python -m venv venv
    call venv\Scripts\activate
    echo Installing dependencies...
    pip install -r ..\requirements.txt scikit-learn joblib pandas numpy
) else (
    call venv\Scripts\activate
)

python server.py
pause
