@echo off
rem Arregla UNA vez el estado roto: cancela el rebase, alinea con GitHub y quita las marcas de conflicto.
git rebase --abort >nul 2>&1
git checkout main || exit /b 1
git fetch origin --tags || exit /b 1
git reset --hard origin/main || exit /b 1
rem El archivo limpio es el del tag v1.1.2 (es lo unico que diferia).
git checkout v1.1.2 -- gradle/libs.versions.toml || exit /b 1
git add -A
git commit -m "fix: quitar marcas de conflicto de libs.versions.toml (v1.1.2)" || exit /b 1
git push origin main || exit /b 1
echo.
echo Listo. main esta limpio y sincronizado.
