@echo off
title OmniBus Modern Bus Reservation Platform
echo =====================================================
echo   Starting OmniBus Modern Bus Reservation Platform
echo =====================================================
echo.
echo Launching browser at http://localhost:4200 ...
start http://localhost:4200
echo.
cd /d "%~dp0angularplay"
npm start
pause
