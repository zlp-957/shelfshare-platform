@echo off
chcp 65001 >nul
title BookLoop - Frontend
echo ============================================
echo   Start Frontend (Vue3 + Vite)
echo ============================================
echo.

set "NODE_HOME=C:\Users\zlp\.workbuddy\binaries\node\versions\22.22.2-6"
set "PROJECT_DIR=%~dp0"
set "NPM=%NODE_HOME%\npm.cmd"

if not exist "%NODE_HOME%\node.exe" (
    echo [ERROR] Node not found: %NODE_HOME%
    pause
    exit /b 1
)

REM Add node directory to PATH so npm/vite can find the "node" command
set "PATH=%NODE_HOME%;%PATH%"

if not exist "%PROJECT_DIR%frontend\node_modules" (
    echo [INFO] Installing frontend dependencies...
    cd /d "%PROJECT_DIR%frontend"
    call "%NPM%" install
    if errorlevel 1 (
        echo [ERROR] Frontend dependency install failed.
        pause
        exit /b 1
    )
)

echo Frontend address : http://localhost:5173
echo Press Ctrl+C or close this window to stop.
echo.
echo ---------- Startup log ----------

cd /d "%PROJECT_DIR%frontend"
call "%NPM%" run dev

pause
