@echo off
title Medical Drone - Spring Boot Backend (:8080)
echo ========================================================
echo Starting Spring Boot Core Backend (Port 8080)
echo Bengaluru 4-Zone + Central Hub Medical Delivery Platform
echo ========================================================
echo.
echo Database Selection:
echo [1] MySQL / MySQL Workbench (Database: medical_drone_db, port 3306) [RECOMMENDED]
echo [2] In-Memory Test Mode (Zero-config H2, no password needed)
echo [3] PostgreSQL (Database: medical, port 5432)
set /p MODE="Select mode [1, 2 or 3, default 1]: "
if "%MODE%"=="" set MODE=1

if "%MODE%"=="2" (
    echo.
    echo Starting with In-Memory Database (dev profile)...
    cd /d "%~dp0backend-spring"
    if exist "mvnw.cmd" (
        call mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
    ) else (
        mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
    )
) else if "%MODE%"=="3" (
    echo.
    echo Enter your PostgreSQL password (press Enter for default 'postgres'):
    set /p DB_PASS="Password: "
    if "%DB_PASS%"=="" set DB_PASS=postgres
    
    cd /d "%~dp0backend-spring"
    mvn spring-boot:run "-Dspring-boot.run.profiles=postgres" "-Dspring-boot.run.arguments=--spring.datasource.password=%DB_PASS%"
) else (
    echo.
    echo Enter your MySQL root password (press Enter for default 'root'):
    set /p DB_PASS="Password: "
    if "%DB_PASS%"=="" set DB_PASS=root
    
    cd /d "%~dp0backend-spring"
    if exist "mvnw.cmd" (
        call mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=mysql" "-Dspring-boot.run.arguments=--spring.datasource.password=%DB_PASS%"
    ) else (
        mvn spring-boot:run "-Dspring-boot.run.profiles=mysql" "-Dspring-boot.run.arguments=--spring.datasource.password=%DB_PASS%"
    )
)
pause
