@echo off
rem Uso:  release 1.1.3 "mensaje del commit"
if "%~1"=="" (
  echo Uso: release 1.1.3 "mensaje del commit"
  exit /b 1
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0release.ps1" -Version "%~1" -Message "%~2"
