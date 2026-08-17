#!/usr/bin/env bash
# =======================================================================
# OmniBus Full Platform Orchestrator (Bash / Linux / macOS / WSL)
# =======================================================================

set -e
BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "======================================================================="
echo "          Starting OmniBus Cloud-Native Platform Locally               "
echo "======================================================================="
echo ""

# 1. Start Infrastructure Services (RabbitMQ, Valkey)
echo "[1/6] Starting Infrastructure Services (RabbitMQ & Valkey)..."
if command -v podman &> /dev/null; then
    podman machine start 2>/dev/null || true
    podman run -d --name bus-rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management-alpine 2>/dev/null || true
    podman run -d --name bus-valkey -p 6379:6379 valkey/valkey:8.0-alpine 2>/dev/null || true
elif command -v docker &> /dev/null; then
    docker run -d --name bus-rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management-alpine 2>/dev/null || true
    docker run -d --name bus-valkey -p 6379:6379 valkey/valkey:8.0-alpine 2>/dev/null || true
fi

# 2. Start Keycloak 26+
echo ""
echo "[2/6] Starting Keycloak 26+ IAM Server (Port 8088)..."
if [ -f "$BASE_DIR/tools/keycloak-26.0.7/bin/kc.sh" ]; then
    (cd "$BASE_DIR/tools/keycloak-26.0.7/bin" && ./kc.sh start-dev --http-port=8088 --import-realm > "$BASE_DIR/keycloak.log" 2>&1 &)
fi

# 3. Start Service Registry & Gateway
echo ""
echo "[3/6] Starting Service Registry & API Gateway..."
infra_services=("service-registry" "gateway")
for svc in "${infra_services[@]}"; do
    echo "  -> Starting $svc..."
    if [ -f "$BASE_DIR/tools/apache-maven-3.9.9/bin/mvn" ]; then
        (cd "$BASE_DIR/$svc" && "$BASE_DIR/tools/apache-maven-3.9.9/bin/mvn" spring-boot:run > "$BASE_DIR/$svc.log" 2>&1 &)
    else
        (cd "$BASE_DIR/$svc" && mvn spring-boot:run > "$BASE_DIR/$svc.log" 2>&1 &)
    fi
    sleep 3
done

# 4. Start Spring Boot Microservices
echo ""
echo "[4/6] Starting Spring Boot Microservices..."
services=("inventoryservice" "adminservice" "bookingservice" "paymentservice")

for svc in "${services[@]}"; do
    echo "  -> Starting $svc..."
    if [ -f "$BASE_DIR/tools/apache-maven-3.9.9/bin/mvn" ]; then
        (cd "$BASE_DIR/$svc" && "$BASE_DIR/tools/apache-maven-3.9.9/bin/mvn" spring-boot:run > "$BASE_DIR/$svc.log" 2>&1 &)
    else
        (cd "$BASE_DIR/$svc" && mvn spring-boot:run > "$BASE_DIR/$svc.log" 2>&1 &)
    fi
    sleep 3
done

# 5. Start Frontend UI
echo ""
echo "[5/6] Launching Modern Angular 21 Frontend (Port 4200)..."
(cd "$BASE_DIR/angularplay" && npm start > "$BASE_DIR/angularplay.log" 2>&1 &)

echo ""
echo "======================================================================="
echo "  OmniBus Stack is Running!"
echo "  - Frontend UI:        http://localhost:4200"
echo "  - API Gateway:        http://localhost:8080"
echo "  - Eureka Dashboard:   http://localhost:8761"
echo "  - RabbitMQ Console:   http://localhost:15672 (guest / guest)"
echo "  - Keycloak Admin:     http://localhost:8088/admin (admin / admin)"
echo "  - Service Registry:   http://localhost:8761"
echo "  - Admin Service:      http://localhost:8081"
echo "  - Booking Service:    http://localhost:8083"
echo "  - Inventory Service:  http://localhost:8084"
echo "  - Payment Service:    http://localhost:8085"
echo ""
echo "  To stop all services, run ./stop-all.sh"
echo "======================================================================="
