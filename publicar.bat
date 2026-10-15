@echo off
REM Doble clic o: publicar.bat [minor|major|1.3.0|--actual|-m "notas"]
REM Necesita Git para Windows (incluye Git Bash).
where bash >nul 2>nul || (echo No encuentro bash. Instala Git para Windows: https://git-scm.com/download/win & pause & exit /b 1)
bash "%~dp0publicar.sh" %*
pause
