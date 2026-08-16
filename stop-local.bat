@echo off
echo Stopping OmniBus local dev servers...
taskkill /F /IM node.exe 2>nul
echo OmniBus servers stopped.
pause
