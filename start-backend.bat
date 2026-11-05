@echo off
chcp 65001 >nul
title BookLoop - Backend
echo ============================================
echo   Start Backend (SpringBoot 2.7)
echo ============================================
echo.

set "JAVA_HOME=C:\Users\zlp\AppData\Local\Programs\Eclipse Adoptium\jdk-11.0.30.7-hotspot"
set "PROJECT_DIR=%~dp0"
set "BACKEND_PORT=8080"

REM 加载本地私有配置（数据库密码、回调地址等）。
REM 这些值不写进 application.yml，避免随仓库泄露；
REM 没有该文件时密码为空，后端会在连库时报错——按提示创建即可
if exist "%PROJECT_DIR%env.local.bat" (
    call "%PROJECT_DIR%env.local.bat"
) else (
    echo [WARN] env.local.bat not found.
    echo        Copy env.local.bat.example to env.local.bat and fill in DB_PASSWORD.
    echo.
)

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [ERROR] JDK not found: %JAVA_HOME%
    pause
    exit /b 1
)

if not exist "%PROJECT_DIR%backend\target\library-management-1.0.0.jar" (
    echo [ERROR] Backend jar not found. Build first: cd backend ^&^& mvn clean package -DskipTests
    pause
    exit /b 1
)

echo Backend port : %BACKEND_PORT%
echo API address  : http://localhost:%BACKEND_PORT%
echo Press Ctrl+C or close this window to stop.
echo.
echo ---------- Startup log ----------

"%JAVA_HOME%\bin\java.exe" -jar "%PROJECT_DIR%backend\target\library-management-1.0.0.jar" --server.port=%BACKEND_PORT%

pause
