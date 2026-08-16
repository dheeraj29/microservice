# Stop OmniBus Local Servers
Write-Host "Stopping OmniBus local dev servers..." -ForegroundColor Yellow
Get-Process -Name "node" -ErrorAction SilentlyContinue | Stop-Process -Force
Write-Host "OmniBus servers stopped successfully." -ForegroundColor Green
