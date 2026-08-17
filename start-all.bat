@echo off
title OmniBus Full Platform Orchestrator
echo =======================================================================
echo          Starting OmniBus Cloud-Native Platform Locally
echo =======================================================================
echo.

set "BASE_DIR=%~dp0"
set "MVN_CMD=%BASE_DIR%tools\apache-maven-3.9.9\bin\mvn.cmd"
set "KC_BIN=%BASE_DIR%tools\keycloak-26.0.7\bin"

echo [1/6] Starting Infrastructure Services (RabbitMQ and Valkey)...
where podman >nul 2>nul
if %ERRORLEVEL% equ 0 goto :START_PODMAN
where docker >nul 2>nul
if %ERRORLEVEL% equ 0 goto :START_DOCKER
goto :INFRA_STANDALONE

:START_PODMAN
echo Podman detected. Launching RabbitMQ and Valkey containers...
podman machine start 2>nul
podman run -d --name bus-rabbitmq --network host docker.io/library/rabbitmq:3-management-alpine 2>nul
podman start bus-rabbitmq 2>nul
podman run -d --name bus-valkey --network host docker.io/valkey/valkey:8.0-alpine 2>nul
podman start bus-valkey 2>nul
goto :START_KEYCLOAK

:START_DOCKER
echo Docker detected. Launching RabbitMQ and Valkey containers...
docker run -d --name bus-rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management-alpine 2>nul
docker start bus-rabbitmq 2>nul
docker run -d --name bus-valkey -p 6379:6379 valkey/valkey:8.0-alpine 2>nul
docker start bus-valkey 2>nul
goto :START_KEYCLOAK

:INFRA_STANDALONE
echo Container engine not detected. Proceeding with embedded services...
goto :START_KEYCLOAK

:START_KEYCLOAK
echo.
echo [2/6] Starting Keycloak 26+ IAM Server on port 8088...
if exist "%KC_BIN%\kc.bat" (
    start "OmniBus - Keycloak IAM (Port 8088)" /min cmd /c "cd /d "%KC_BIN%" && kc.bat start-dev --http-port=8088"
) else (
    echo Keycloak directory not found in tools.
)

echo.
echo [3/6] Starting Service Registry - Eureka (Port 8761)...
start "OmniBus - Service Registry" /min cmd /c "cd /d "%BASE_DIR%service-registry" && "%MVN_CMD%" spring-boot:run"
timeout /t 5 >nul

echo.
echo [4/6] Starting API Gateway & Auth Orchestrator (Port 8080)...
start "OmniBus - Gateway" /min cmd /c "cd /d "%BASE_DIR%gateway" && "%MVN_CMD%" spring-boot:run"
timeout /t 4 >nul

echo.
echo [5/6] Starting Spring Boot Microservices...
echo Starting Inventory Service (Port 8084)...
start "OmniBus - Inventory Service" /min cmd /c "cd /d "%BASE_DIR%inventoryservice" && "%MVN_CMD%" spring-boot:run"

echo Starting Admin Service (Port 8081)...
start "OmniBus - Admin Service" /min cmd /c "cd /d "%BASE_DIR%adminservice" && "%MVN_CMD%" spring-boot:run"

echo Starting Booking Service (Port 8083)...
start "OmniBus - Booking Service" /min cmd /c "cd /d "%BASE_DIR%bookingservice" && "%MVN_CMD%" spring-boot:run"

echo Starting Payment Service (Port 8085)...
start "OmniBus - Payment Service" /min cmd /c "cd /d "%BASE_DIR%paymentservice" && "%MVN_CMD%" spring-boot:run"

echo.
echo [6/6] Launching Modern Angular 21 Frontend (Port 4200)...
start "OmniBus - Angular UI" cmd /c "cd /d "%BASE_DIR%angularplay" && npm start"

echo.
echo Opening Web Application...
timeout /t 5 >nul
start http://localhost:4200

echo.
echo =======================================================================
echo  OmniBus Full Enterprise Stack is Running!
echo  - Frontend UI:        http://localhost:4200
echo  - API Gateway:        http://localhost:8080
echo  - Eureka Dashboard:   http://localhost:8761
echo  - RabbitMQ Console:   http://localhost:15672 - guest / guest
echo  - Keycloak Admin:     http://localhost:8088/admin - admin / admin
echo  - Admin Service:      http://localhost:8081
echo  - Booking Service:    http://localhost:8083
echo  - Inventory Service:  http://localhost:8084
echo  - Payment Service:    http://localhost:8085
echo.
echo  To stop all services at any time, run stop-all.bat
echo =======================================================================
pause
