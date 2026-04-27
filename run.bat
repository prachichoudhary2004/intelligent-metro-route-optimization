@echo off
echo Starting Intelligent Metro Navigator Services...

echo.
echo [1/3] Starting Java Route Engine...
start "Java Engine" cmd /k "cd java-engine && java -cp .;lib\json-simple.jar src.main.Main"
timeout /t 3 /nobreak > nul

echo.
echo [2/3] Starting ML Prediction Services...
start "ML Services" cmd /k "cd ml-services && python app.py"
timeout /t 5 /nobreak > nul

echo.
echo [3/3] Starting Dashboard...
start "Dashboard" cmd /k "cd dashboard && python -m http.server 8080"
timeout /t 2 /nobreak > nul

echo.
echo All services started!
echo.
echo Services:
echo - Java Engine: Running (console window)
echo - ML Services: http://localhost:5000
echo - Dashboard: http://localhost:8080
echo.
echo Press any key to open dashboard in browser...
pause > nul
start http://localhost:8080

echo.
echo To stop all services, close the console windows.
pause
