@echo off
echo Building Intelligent Metro Route Optimization System...

REM Create necessary directories
echo Creating directories...
if not exist "java\core" mkdir "java\core"
if not exist "java\algorithms" mkdir "java\algorithms"
if not exist "java\utils" mkdir "java\utils"
if not exist "python\models" mkdir "python\models"
if not exist "python\utils" mkdir "python\utils"
if not exist "data" mkdir "data"

REM Compile Java components
echo Compiling Java components...
cd java
set CLASSPATH=.;..\lib\*
javac -cp "%CLASSPATH%" core\*.java
javac -cp "%CLASSPATH%" utils\*.java
javac -cp "%CLASSPATH%" algorithms\*.java
javac -cp "%CLASSPATH%" MetroRouteAPI.java
cd ..

REM Setup Python environment
echo Setting up Python environment...
cd python
pip install flask flask-cors scikit-learn pandas numpy
cd ..

echo.
echo [3/3] Preparing Dashboard...
cd dashboard
echo Dashboard ready!
cd ..

echo.
echo Build completed successfully!
echo Run 'run.bat' to start all services.
pause
