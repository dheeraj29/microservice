# =======================================================================
# OmniBus Full Platform Orchestrator with Real-Time Health Checking
# =======================================================================

Write-Host "=======================================================================" -ForegroundColor Cyan
Write-Host "          Starting OmniBus Cloud-Native Platform Locally               " -ForegroundColor Yellow
Write-Host "=======================================================================" -ForegroundColor Cyan

$BaseDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$MvnCmd = Join-Path $BaseDir "tools\apache-maven-3.9.9\bin\mvn.cmd"
$KcCmd = Join-Path $BaseDir "tools\keycloak-26.0.7\bin\kc.bat"
$KcBin = Join-Path $BaseDir "tools\keycloak-26.0.7\bin"

# Health Check Helper Function
function Wait-ForService ($name, $url, $timeoutSeconds = 45) {
    Write-Host "  -> Waiting for $name ($url)... " -NoNewline -ForegroundColor Gray
    $startTime = Get-Date
    while (((Get-Date) - $startTime).TotalSeconds -lt $timeoutSeconds) {
        try {
            $resp = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
            if ($resp.StatusCode -ge 200 -and $resp.StatusCode -lt 500) {
                Write-Host "[UP - HTTP $($resp.StatusCode)]" -ForegroundColor Green
                return $true
            }
        } catch {
            if ($_.Exception.Response -and $_.Exception.Response.StatusCode.value__ -lt 500) {
                Write-Host "[UP]" -ForegroundColor Green
                return $true
            }
            Start-Sleep -Seconds 2
        }
    }
    Write-Host "[TIMEOUT - check logs]" -ForegroundColor DarkYellow
    return $false
}

# 1. Start RabbitMQ & Valkey Infrastructure
Write-Host "`n[1/6] Starting Infrastructure Services (RabbitMQ & Valkey)..." -ForegroundColor Green
$hasPodman = Get-Command podman -ErrorAction SilentlyContinue
$hasDocker = Get-Command docker -ErrorAction SilentlyContinue

if ($hasPodman) {
    podman machine start 2>$null
    podman run -d --name bus-rabbitmq --network host docker.io/library/rabbitmq:3-management-alpine 2>$null
    podman start bus-rabbitmq 2>$null
    podman run -d --name bus-valkey --network host docker.io/valkey/valkey:8.0-alpine 2>$null
    podman start bus-valkey 2>$null
    $null = Wait-ForService "RabbitMQ Management" "http://localhost:15672" 25
} elseif ($hasDocker) {
    docker run -d --name bus-rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management-alpine 2>$null
    docker start bus-rabbitmq 2>$null
    docker run -d --name bus-valkey -p 6379:6379 valkey/valkey:8.0-alpine 2>$null
    docker start bus-valkey 2>$null
    $null = Wait-ForService "RabbitMQ Management" "http://localhost:15672" 25
} else {
    Write-Host "Note: Container engine not active. Valkey & RabbitMQ must be running on ports 6379 & 5672." -ForegroundColor DarkYellow
}

# 2. Start Keycloak
Write-Host "`n[2/6] Starting Keycloak 26+ IAM Server (Port 8088)..." -ForegroundColor Green
if (Test-Path $KcCmd) {
    Start-Process -FilePath "cmd.exe" -ArgumentList "/c cd /d `"$KcBin`" && kc.bat start-dev --http-port=8088 --import-realm" -WindowStyle Minimized
    $null = Wait-ForService "Keycloak IAM" "http://localhost:8088/realms/bus-reservation" 40
} else {
    Write-Host "Keycloak not found in tools directory." -ForegroundColor Red
}

# 3. Start Service Registry (Eureka on port 8761)
Write-Host "`n[3/6] Starting Service Registry (Eureka)..." -ForegroundColor Green
$regPath = Join-Path $BaseDir "service-registry"
Start-Process -FilePath "cmd.exe" -ArgumentList "/c cd /d `"$regPath`" && `"$MvnCmd`" spring-boot:run" -WindowStyle Minimized
$null = Wait-ForService "Service Registry (Eureka)" "http://localhost:8761" 35

# 4. Start API Gateway (Port 8080)
Write-Host "`n[4/6] Starting API Gateway (Port 8080)..." -ForegroundColor Green
$gwPath = Join-Path $BaseDir "gateway"
Start-Process -FilePath "cmd.exe" -ArgumentList "/c cd /d `"$gwPath`" && `"$MvnCmd`" spring-boot:run" -WindowStyle Minimized
$null = Wait-ForService "API Gateway" "http://localhost:8080/actuator/health" 35

# 5. Start Backend Microservices
Write-Host "`n[5/6] Starting Core Microservices..." -ForegroundColor Green

# 5.1 Inventory Service (Port 8084)
$invPath = Join-Path $BaseDir "inventoryservice"
Start-Process -FilePath "cmd.exe" -ArgumentList "/c cd /d `"$invPath`" && `"$MvnCmd`" spring-boot:run" -WindowStyle Minimized
$null = Wait-ForService "Inventory Service" "http://localhost:8084/inventoryservice/v1/busSeatLayout/101" 35

# 5.2 Admin Service (Port 8081)
$admPath = Join-Path $BaseDir "adminservice"
Start-Process -FilePath "cmd.exe" -ArgumentList "/c cd /d `"$admPath`" && `"$MvnCmd`" spring-boot:run" -WindowStyle Minimized
$null = Wait-ForService "Admin Service" "http://localhost:8081/adminservice/v1/allBuses" 35

# 5.3 Booking Service (Port 8083)
$bkgPath = Join-Path $BaseDir "bookingservice"
Start-Process -FilePath "cmd.exe" -ArgumentList "/c cd /d `"$bkgPath`" && `"$MvnCmd`" spring-boot:run" -WindowStyle Minimized
$null = Wait-ForService "Booking Service" "http://localhost:8083/bookingservice/v1/myBookings?username=admin" 35

# 5.4 Payment Service (Port 8085)
$payPath = Join-Path $BaseDir "paymentservice"
Start-Process -FilePath "cmd.exe" -ArgumentList "/c cd /d `"$payPath`" && `"$MvnCmd`" spring-boot:run" -WindowStyle Minimized
$null = Wait-ForService "Payment Service" "http://localhost:8085/paymentservice/v1/orders/1" 35

# 6. Start Angular Frontend (Port 4200)
Write-Host "`n[6/6] Launching Modern Angular 21 Frontend (Port 4200)..." -ForegroundColor Green
$angPath = Join-Path $BaseDir "angularplay"
Start-Process -FilePath "cmd.exe" -ArgumentList "/c cd /d `"$angPath`" && npm start"

# Open Browser
Start-Sleep -Seconds 3
Start-Process "http://localhost:4200"

Write-Host "`n=======================================================================" -ForegroundColor Cyan
Write-Host " OmniBus Full Enterprise Stack is Running!" -ForegroundColor Yellow
Write-Host " - Frontend UI:        http://localhost:4200" -ForegroundColor White
Write-Host " - API Gateway:        http://localhost:8080" -ForegroundColor White
Write-Host "  - Eureka Dashboard:   http://localhost:8761" -ForegroundColor White
Write-Host "  - RabbitMQ Console:   http://localhost:15672 (guest / guest)" -ForegroundColor White
Write-Host "  - Keycloak Admin:     http://localhost:8088/admin (admin / admin)" -ForegroundColor White
Write-Host "  - Admin Service:      http://localhost:8081" -ForegroundColor White
Write-Host "  - Booking Service:    http://localhost:8083" -ForegroundColor White
Write-Host "  - Inventory Service:  http://localhost:8084" -ForegroundColor White
Write-Host "  - Payment Service:    http://localhost:8085" -ForegroundColor White
Write-Host "`n  To stop all services at any time, run .\stop-all.ps1" -ForegroundColor DarkGray
Write-Host "=======================================================================" -ForegroundColor Cyan
