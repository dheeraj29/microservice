@echo off
title Stop OmniBus Stack
echo =======================================================================
echo              Stopping OmniBus Platform Services
echo =======================================================================
echo.

echo [1/3] Stopping Java Microservices and Keycloak...
taskkill /F /IM java.exe 2>nul

echo [2/3] Stopping Angular Dev Server...
taskkill /F /IM node.exe 2>nul

echo [3/3] Stopping Infrastructure Containers (RabbitMQ and Valkey)...
where podman >nul 2>nul
if %ERRORLEVEL% equ 0 (
    podman stop bus-rabbitmq bus-valkey bus-keycloak 2>nul
    podman rm -f bus-rabbitmq bus-valkey bus-keycloak 2>nul
)

where docker >nul 2>nul
if %ERRORLEVEL% equ 0 (
    docker stop bus-rabbitmq bus-valkey bus-keycloak 2>nul
    docker rm -f bus-rabbitmq bus-valkey bus-keycloak 2>nul
)

echo.
echo All OmniBus processes and containers stopped successfully.
pause
