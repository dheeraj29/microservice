# Stop All OmniBus Services
Write-Host "Stopping OmniBus Platform Services..." -ForegroundColor Yellow

# 1. Stop Java processes
Write-Host "Stopping Java Microservices..." -ForegroundColor Gray
Get-Process -Name "java" -ErrorAction SilentlyContinue | Stop-Process -Force

# 2. Stop Node processes
Write-Host "Stopping Angular Dev Server..." -ForegroundColor Gray
Get-Process -Name "node" -ErrorAction SilentlyContinue | Stop-Process -Force

# 3. Stop containers
$hasPodman = Get-Command podman -ErrorAction SilentlyContinue
if ($hasPodman) {
    Write-Host "Stopping Infrastructure Containers..." -ForegroundColor Gray
    podman stop bus-valkey bus-rabbitmq bus-keycloak 2>$null
    podman rm -f bus-valkey bus-rabbitmq bus-keycloak 2>$null
}

Write-Host "`nAll OmniBus processes and containers stopped." -ForegroundColor Green
