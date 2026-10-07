@echo off
title Intelligent Medical Drone Management Launcher
echo =========================================================================
echo Launching Intelligent Medical Drone Delivery System
echo Architecture: Bengaluru 4-Zone + Central Hub Architecture
echo Services:
echo   1. Python FastAPI AI Decision Service (Port 8000)
echo   2. Spring Boot 3 Core Backend (Port 8080)
echo   3. React Vite Frontend (Port 5173)
echo =========================================================================

echo Starting FastAPI AI Service in a new terminal...
start "AI-Service-FastAPI" cmd /k "%~dp0run_ai_service.bat"

timeout /t 3 /nobreak >nul

echo Starting Spring Boot Backend in a new terminal...
start "Backend-SpringBoot" cmd /k "%~dp0run_backend.bat"

timeout /t 5 /nobreak >nul

echo Starting React Frontend in a new terminal...
start "Frontend-React" cmd /k "%~dp0run_frontend.bat"

echo.
echo All services are launching!
echo Frontend will be accessible at: http://localhost:5173
echo.
pause
