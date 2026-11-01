@echo off
powershell -ExecutionPolicy Bypass -File "%~dp0scripts\build-apk.ps1" %*
