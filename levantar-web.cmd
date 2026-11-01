@echo off
cd /d "%~dp0"
start "Lark Backend" powershell -NoExit -Command "Set-Location '%~dp0backend'; python -m uvicorn server:app --host 127.0.0.1 --port 8000"
start "Lark Web" powershell -NoExit -Command "Set-Location '%~dp0frontend'; npm run web -- --port 8081"
