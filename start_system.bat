@echo off
setlocal enabledelayedexpansion

echo ============================================================
echo   METRO NAVIGATOR: INTELLIGENT ROUTE OPTIMIZATION SYSTEM
echo ============================================================
echo.

:: 1. Setup ML Service
echo [1/3] Initializing ML Prediction Service...
cd ml-services
if not exist "models\congestion_model.joblib" (
    echo [!] Models not found. Training now...
    python train_models.py
)
start "ML Service (Port 5000)" cmd /k "python app.py"
cd ..
timeout /t 3 /nobreak >nul

:: 2. Build and Start Java API
echo [2/3] Building and Starting Java API Engine...
cd java
:: Check for dependencies in lib
javac -cp ".;..\lib\*" *.java core/*.java algorithms/*.java utils/*.java
if %errorlevel% neq 0 (
    echo [ERROR] Java compilation failed. Please check dependencies.
    pause
    exit /b %errorlevel%
)
start "Java API (Port 8081)" cmd /k "java -cp ".;..\lib\*" MetroRouteAPI"
cd ..
timeout /t 2 /nobreak >nul

:: 3. Start Frontend Dashboard
echo [3/3] Launching Dashboard...
echo.
echo ============================================================
echo   SUCCESS: All systems operational.
echo ============================================================
echo.
echo 🌐 Dashboard: http://localhost:8080/dashboard/index.html
echo 🚇 Routing API: http://localhost:8081/api/route
echo 🤖 ML Service: http://localhost:5000/api/health
echo.

:: Use Python to serve the frontend on 8080 for a more professional feel
echo [INFO] Starting light-weight frontend server on port 8080...
start "Frontend Server" cmd /c "python -m http.server 8080"
timeout /t 1 /nobreak >nul
start http://localhost:8080/dashboard/index.html

echo.
echo Press any key to shutdown all servers...
pause >nul

:: Cleanup
taskkill /FI "WINDOWTITLE eq ML Service*" /F >nul 2>&1
taskkill /FI "WINDOWTITLE eq Java API*" /F >nul 2>&1
taskkill /FI "WINDOWTITLE eq Frontend Server*" /F >nul 2>&1

echo Systems stopped. Goodbye!
pause
