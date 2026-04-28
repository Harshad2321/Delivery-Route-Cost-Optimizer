@echo off
REM Delivery Route Cost Optimizer - Web Server Startup Script
REM Windows Batch Script

echo Delivery Route Cost Optimizer - Web Server
echo ==========================================
echo.

REM Check if Maven is installed
where mvn >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo ERROR: Maven is not installed or not in PATH
    echo Please install Maven and add it to your system PATH
    pause
    exit /b 1
)

REM Check if Java is installed
where java >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo ERROR: Java is not installed or not in PATH
    echo Please install Java 17 or higher and add it to your system PATH
    pause
    exit /b 1
)

echo [INFO] Building project...
call mvn clean compile
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Build failed
    pause
    exit /b 1
)

echo.
echo [INFO] Starting web server...
echo.
call mvn exec:java
pause
