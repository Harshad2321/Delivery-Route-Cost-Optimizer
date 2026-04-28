#!/usr/bin/env pwsh

# Delivery Route Cost Optimizer - Web Server Startup Script
# PowerShell Script

Write-Host "Delivery Route Cost Optimizer - Web Server" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

# Check if Maven is installed
try {
    $mavenVersion = mvn -v 2>$null
    Write-Host "[INFO] Found Maven" -ForegroundColor Green
} catch {
    Write-Host "[ERROR] Maven is not installed or not in PATH" -ForegroundColor Red
    Write-Host "Please install Maven and add it to your system PATH" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}

# Check if Java is installed
try {
    $javaVersion = java -version 2>&1 | Select-String "java version"
    Write-Host "[INFO] Found Java" -ForegroundColor Green
} catch {
    Write-Host "[ERROR] Java is not installed or not in PATH" -ForegroundColor Red
    Write-Host "Please install Java 17 or higher and add it to your system PATH" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}

# Build the project
Write-Host ""
Write-Host "[INFO] Building project..." -ForegroundColor Yellow
mvn clean compile
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Build failed" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}

# Start the web server
Write-Host ""
Write-Host "[INFO] Starting web server..." -ForegroundColor Yellow
Write-Host ""
mvn exec:java

Read-Host "Press Enter to exit"
