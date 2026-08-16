#!/usr/bin/env bash
# =======================================================================
# Stop All OmniBus Platform Services (Linux / macOS / WSL)
# =======================================================================

echo "Stopping OmniBus Platform Services..."

echo "[1/3] Stopping Java processes..."
pkill -f "spring-boot:run" 2>/dev/null || true
pkill -f "kc.sh" 2>/dev/null || true

echo "[2/3] Stopping Node / Angular server..."
pkill -f "ng serve" 2>/dev/null || true

echo "[3/3] Stopping Infrastructure Containers (RabbitMQ & Valkey)..."
if command -v podman &> /dev/null; then
    podman stop bus-rabbitmq bus-valkey bus-keycloak 2>/dev/null || true
    podman rm -f bus-rabbitmq bus-valkey bus-keycloak 2>/dev/null || true
elif command -v docker &> /dev/null; then
    docker stop bus-rabbitmq bus-valkey bus-keycloak 2>/dev/null || true
    docker rm -f bus-rabbitmq bus-valkey bus-keycloak 2>/dev/null || true
fi

echo ""
echo "All OmniBus services and containers stopped."
