@echo off
title Medical Drone - React Frontend (:5173)
echo ===================================================
echo Starting React Frontend (Port 5173)
echo ===================================================
cd /d "%~dp0frontend"
if not exist "node_modules\" (
    echo Installing npm dependencies...
    call npm install
)
npm run dev
pause
