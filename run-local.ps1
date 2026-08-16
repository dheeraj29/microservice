# =====================================================================
# OmniBus Modern Web App - 1-Click Local Runner
# =====================================================================

Write-Host "=====================================================" -ForegroundColor Cyan
Write-Host "  Starting OmniBus Modern Bus Reservation Platform   " -ForegroundColor Yellow
Write-Host "=====================================================" -ForegroundColor Cyan

$CurrentDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$AngularDir = Join-Path $CurrentDir "angularplay"

Write-Host "`n[1/2] Checking dependencies in $AngularDir..." -ForegroundColor Green
Set-Location -Path $AngularDir

Write-Host "`n[2/2] Launching OmniBus Web Server on http://localhost:4200..." -ForegroundColor Green
Start-Process "http://localhost:4200"

# Start the dev server
cmd.exe /c npm start
